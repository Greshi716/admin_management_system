package com.admin.management.fees;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.admin.management.masterdata.api.MasterDataLookup;
import com.admin.management.masterdata.api.MasterDataLookup.StandardOption;
import com.admin.management.shared.web.ApiProblem.FieldError;
import com.admin.management.shared.web.CodedConflictException;
import com.admin.management.shared.web.ConflictException;
import com.admin.management.shared.web.NotFoundException;
import com.admin.management.shared.web.StaleVersionException;
import com.admin.management.shared.web.ValidationException;
import com.admin.management.students.api.EnrollmentLookup;

/**
 * Reads and changes the fee structure of a year (FR-001 to FR-003, FR-012, FR-013). Every change of a row takes the
 * year's status row for writing, creates it on first use and bumps its version, so two windows cannot both change
 * the structure from the same starting point. A change to a Finalised year is a revise (FR-004): without the
 * admin's confirmation nothing is changed. The fee API works for any year; the current-year rule is on the screen.
 */
@Service
class FeeStructureService {

	static final String REVISE_CONFIRMATION_REQUIRED = "REVISE_CONFIRMATION_REQUIRED";

	static final String DUPLICATE_MESSAGE = "A fee row for this standard and category already exists for this year.";

	private final FeeRowRepository rows;

	private final FeeYearStatusRepository statuses;

	private final MasterDataLookup masterData;

	private final EnrollmentLookup enrollments;

	private final FeesAudit audit;

	private final FeeAccountSync accountSync;

	private final Clock clock;

	FeeStructureService(FeeRowRepository rows, FeeYearStatusRepository statuses, MasterDataLookup masterData,
			EnrollmentLookup enrollments, FeesAudit audit, FeeAccountSync accountSync, Clock clock) {
		this.accountSync = accountSync;
		this.clock = clock;
		this.rows = rows;
		this.statuses = statuses;
		this.masterData = masterData;
		this.enrollments = enrollments;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	FeeStructureDto structure(Long yearId) {
		return toStructure(yearId);
	}

	@Transactional(readOnly = true)
	FeeChoicesDto choices(Long yearId) {
		requireYear(yearId);
		List<FeeRow> held = rows.findByAcademicYearIdOrderById(yearId);
		Set<Long> heldStandards = held.stream().map(FeeRow::getStandardId).collect(Collectors.toSet());
		Set<Long> heldCategories = held.stream()
			.map(FeeRow::getCategoryId)
			.filter(Objects::nonNull)
			.collect(Collectors.toSet());
		MasterDataLookup.FilterOptions all = masterData.filterOptions();
		return new FeeChoicesDto(
				all.standards()
					.stream()
					.filter(s -> s.active() || heldStandards.contains(s.id()))
					.sorted(Comparator.comparingInt(StandardOption::displayOrder).thenComparing(StandardOption::id))
					.map(s -> new FeeChoicesDto.StandardChoice(s.id(), s.name(), s.displayOrder()))
					.toList(),
				all.categories()
					.stream()
					.filter(c -> c.active() || heldCategories.contains(c.id()))
					.map(c -> new FeeChoicesDto.CategoryChoice(c.id(), c.code(), c.name()))
					.toList());
	}

	@Transactional
	FeeRowDto addRow(Long yearId, FeeRowInput in, boolean confirm) {
		requireYear(yearId);
		boolean revise = lockAndCheck(yearId, in.structureVersion(), confirm);
		validate(yearId, in, null);
		FeeRow row = rows.saveAndFlush(new FeeRow(yearId, in.standardId(), in.categoryId(), in.amount(),
				in.registrationFee(), in.dueDate()));
		audit.rowCreated(row.getId(), row.snapshot(), revise ? FeesAudit.REVISE : null,
				recalculateIfRevise(yearId, revise));
		return toDto(row);
	}

	@Transactional
	FeeRowDto editRow(Long yearId, Long rowId, FeeRowInput in, boolean confirm) {
		requireYear(yearId);
		FeeRow row = rowOfYear(yearId, rowId);
		boolean revise = lockAndCheck(yearId, in.structureVersion(), confirm);
		checkRowVersion(in.version(), row, yearId);
		validate(yearId, in, row);
		Map<String, Object> before = row.snapshot();
		row.change(in.standardId(), in.categoryId(), in.amount(), in.registrationFee(), in.dueDate());
		FeeRow saved = rows.saveAndFlush(row);
		audit.rowEdited(saved.getId(), before, saved.snapshot(), revise ? FeesAudit.REVISE : null,
				recalculateIfRevise(yearId, revise));
		return toDto(saved);
	}

	@Transactional
	void deleteRow(Long yearId, Long rowId, Long version, Long structureVersion, boolean confirm) {
		requireYear(yearId);
		FeeRow row = rowOfYear(yearId, rowId);
		boolean revise = lockAndCheck(yearId, structureVersion, confirm);
		checkRowVersion(version, row, yearId);
		Map<String, Object> values = row.snapshot();
		rows.delete(row);
		// The delete is flushed by the query that reads the year's rows in the recalculation.
		audit.rowDeleted(rowId, values, revise ? FeesAudit.REVISE : null, recalculateIfRevise(yearId, revise));
	}

	/**
	 * Finalises the year's structure (FR-002, FR-006): the status row is taken for writing first, then the rows are
	 * read, every class record gets its account and one audit entry records the counts. All in one transaction, so a
	 * failure at any step (the audit entry included) leaves the year a Draft with no accounts. The confirmation the
	 * admin gives is on the screen only.
	 */
	@Transactional
	FeeStructureDto finalise(Long yearId, Long version) {
		requireYear(yearId);
		Optional<FeeYearStatus> locked = statuses.lockForUpdate(yearId);
		if (!Objects.equals(version, locked.map(FeeYearStatus::getVersion).orElse(null))) {
			throw new StaleVersionException(toStructure(yearId));
		}
		if (locked.isPresent() && locked.get().isFinalised()) {
			throw new ValidationException("rows", "This fee structure is already finalised.");
		}
		long rowCount = rows.countByAcademicYearId(yearId);
		if (locked.isEmpty() || rowCount == 0) {
			throw new ValidationException("rows", "Add at least one fee row before finalising.");
		}
		FeeYearStatus status = locked.get();
		status.finalise(clock.instant());
		statuses.saveAndFlush(status);
		FeesAudit.Recalculated done = accountSync.recalculate(yearId);
		audit.finalised(yearId, done.studentsRecalculated(), done.accountsChanged(), (int) rowCount);
		return toStructure(yearId);
	}

	/**
	 * Takes the year's status row for writing (creating it on first use), refuses a structure the screen did not
	 * load (before any revise warning), refuses a change to a Finalised year that the admin did not confirm, and
	 * bumps the structure version.
	 */
	private boolean lockAndCheck(Long yearId, Long sentVersion, boolean confirm) {
		Optional<FeeYearStatus> locked = statuses.lockForUpdate(yearId);
		Long actual = locked.map(FeeYearStatus::getVersion).orElse(null);
		if (!Objects.equals(sentVersion, actual)) {
			throw new StaleVersionException(toStructure(yearId));
		}
		FeeYearStatus status = locked.orElse(null);
		boolean revise = status != null && status.isFinalised();
		if (revise && !confirm) {
			throw new CodedConflictException(REVISE_CONFIRMATION_REQUIRED, null,
					"Changing a finalised fee structure recalculates the fee of every student of the year.",
					Map.of("studentCount", enrollments.countOfYear(yearId)));
		}
		if (status == null) {
			statuses.saveAndFlush(new FeeYearStatus(yearId));
		}
		statuses.bumpVersion(yearId);
		return revise;
	}

	/**
	 * After a confirmed row change in a Finalised year: recalculates every class record of the year and writes the
	 * accounts that differ (FR-004). Null for a Draft year, which has no accounts to recalculate.
	 */
	private FeesAudit.Recalculated recalculateIfRevise(Long yearId, boolean revise) {
		return revise ? accountSync.recalculate(yearId) : null;
	}

	private void checkRowVersion(Long sent, FeeRow row, Long yearId) {
		if (sent == null) {
			throw new ValidationException("version", "The version is missing. Reload the fee structure and try again.");
		}
		if (!sent.equals(row.getVersion())) {
			throw new StaleVersionException(toStructure(yearId));
		}
	}

	private FeeRow rowOfYear(Long yearId, Long rowId) {
		return rows.findById(rowId)
			.filter(row -> row.getAcademicYearId().equals(yearId))
			.orElseThrow(() -> new NotFoundException("Fee row", rowId));
	}

	private String requireYear(Long yearId) {
		String name = masterData.yearNames().get(yearId);
		if (name == null) {
			throw new NotFoundException("Academic year", yearId);
		}
		return name;
	}

	/** Field rules of a row, then the one-row-per-standard-and-category rule. {@code existing} is the row being edited. */
	private void validate(Long yearId, FeeRowInput in, FeeRow existing) {
		List<FieldError> errors = new ArrayList<>();
		Long standardId = in.standardId();
		if (standardId == null) {
			errors.add(new FieldError("standardId", "Choose a standard."));
		}
		else if (!masterData.standardNames().containsKey(standardId)) {
			errors.add(new FieldError("standardId", "There is no such standard."));
		}
		else if ((existing == null || !standardId.equals(existing.getStandardId()))
				&& !masterData.isStandardActive(standardId)) {
			errors.add(new FieldError("standardId", "This standard is not active."));
		}
		Long categoryId = in.categoryId();
		if (categoryId != null) {
			if (!masterData.categoryNames().containsKey(categoryId)) {
				errors.add(new FieldError("categoryId", "There is no such category."));
			}
			else if ((existing == null || !categoryId.equals(existing.getCategoryId()))
					&& !masterData.isCategoryActive(categoryId)) {
				errors.add(new FieldError("categoryId", "This category is not active."));
			}
		}
		if (in.amount() == null) {
			errors.add(new FieldError("amount", "Enter the yearly fee."));
		}
		else {
			moneyError("amount", in.amount(), errors);
		}
		if (in.registrationFee() != null) {
			moneyError("registrationFee", in.registrationFee(), errors);
		}
		if (!errors.isEmpty()) {
			throw new ValidationException(errors);
		}
		Optional<FeeRow> sameSlot = rows.findByAcademicYearIdAndStandardIdAndCategoryId(yearId, standardId, categoryId);
		if (sameSlot.isPresent() && (existing == null || !sameSlot.get().getId().equals(existing.getId()))) {
			throw new ConflictException(categoryId != null ? "categoryId" : "standardId", DUPLICATE_MESSAGE);
		}
	}

	/** An amount is zero or more with at most two decimals; it is refused, never rounded (FR-011). */
	private static void moneyError(String field, BigDecimal value, List<FieldError> errors) {
		if (value.signum() < 0) {
			errors.add(new FieldError(field, "The amount cannot be negative."));
		}
		else if (value.stripTrailingZeros().scale() > 2) {
			errors.add(new FieldError(field, "Use at most two decimal places."));
		}
	}

	private FeeStructureDto toStructure(Long yearId) {
		String yearName = requireYear(yearId);
		Optional<FeeYearStatus> status = statuses.findById(yearId);
		Map<Long, String> standardNames = masterData.standardNames();
		Map<Long, Integer> orders = masterData.standardDisplayOrders();
		Map<Long, String> categoryNames = masterData.categoryNames();
		List<FeeRowDto> dtos = rows.findByAcademicYearIdOrderById(yearId)
			.stream()
			.sorted(Comparator.comparingInt((FeeRow r) -> orders.getOrDefault(r.getStandardId(), 0))
				.thenComparing(r -> r.getCategoryId() != null)
				.thenComparing(FeeRow::getId))
			.map(row -> toDto(row, standardNames, categoryNames))
			.toList();
		return new FeeStructureDto(yearId, yearName,
				status.map(FeeYearStatus::getStatus).orElse(FeeYearStatus.DRAFT),
				status.map(FeeYearStatus::getVersion).orElse(null), enrollments.countOfYear(yearId), dtos);
	}

	private FeeRowDto toDto(FeeRow row) {
		return toDto(row, masterData.standardNames(), masterData.categoryNames());
	}

	private static FeeRowDto toDto(FeeRow row, Map<Long, String> standardNames, Map<Long, String> categoryNames) {
		return new FeeRowDto(row.getId(), row.getVersion(), row.getStandardId(), standardNames.get(row.getStandardId()),
				row.getCategoryId(),
				row.getCategoryId() == null ? FeeRowDto.ALL_CATEGORIES : categoryNames.get(row.getCategoryId()),
				row.getAmount(), row.getRegistrationFee(), row.getDueDate());
	}

}
