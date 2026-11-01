package com.admin.management.yearchange;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.admin.management.fees.api.FeeCarryForward;
import com.admin.management.fees.api.FeeCarryForward.CarryForwardResult;
import com.admin.management.masterdata.api.MasterDataLookup;
import com.admin.management.masterdata.api.MasterDataLookup.YearDates;
import com.admin.management.masterdata.api.MasterDataLookup.YearRef;
import com.admin.management.shared.ClassRecordFees;
import com.admin.management.shared.CurrentUserProvider;
import com.admin.management.shared.web.ApiProblem.FieldError;
import com.admin.management.shared.web.CodedConflictException;
import com.admin.management.shared.web.NotFoundException;
import com.admin.management.shared.web.StaleVersionException;
import com.admin.management.shared.web.ValidationException;
import com.admin.management.students.api.ClassRecordPromotion;
import com.admin.management.students.api.ClassRecordPromotion.CandidateStudent;
import com.admin.management.students.api.ClassRecordPromotion.PromotionItemOutcome;
import com.admin.management.yearchange.PromotionRules.DivisionInfo;
import com.admin.management.yearchange.PromotionRules.Outcome;
import com.admin.management.yearchange.PromotionRules.PromotionDecision;
import com.admin.management.yearchange.PromotionRules.PromotionDefault;
import com.admin.management.yearchange.PromotionRules.StandardInfo;

/**
 * Service for promotion screen data and executing promotion runs (FR-006 to FR-012, FR-016 to FR-024).
 */
@Service
public class PromotionService {

	private final MasterDataLookup masterData;

	private final ClassRecordPromotion classRecordPromotion;

	private final FeeCarryForward feeCarryForward;

	private final ClassRecordFees classRecordFees;

	private final PromotionRunRepository runs;

	private final PromotionRunItemRepository runItems;

	private final CurrentUserProvider currentUser;

	public PromotionService(MasterDataLookup masterData, ClassRecordPromotion classRecordPromotion,
			FeeCarryForward feeCarryForward, ClassRecordFees classRecordFees,
			PromotionRunRepository runs, PromotionRunItemRepository runItems,
			CurrentUserProvider currentUser) {
		this.masterData = masterData;
		this.classRecordPromotion = classRecordPromotion;
		this.feeCarryForward = feeCarryForward;
		this.classRecordFees = classRecordFees;
		this.runs = runs;
		this.runItems = runItems;
		this.currentUser = currentUser;
	}

	@Transactional(readOnly = true)
	public PromoteScreenDto getPromoteScreen(long standardId) {
		YearRef newYear = masterData.currentYear()
				.orElseThrow(() -> new CodedConflictException("NO_NEW_YEAR_YET", null, "No new academic year has been started yet.", null));

		List<YearDates> earlier = masterData.earlierYears(newYear.startDate());
		if (earlier.isEmpty()) {
			throw new CodedConflictException("NO_NEW_YEAR_YET", null, "No previous academic year exists to promote from.", null);
		}
		YearDates oldYear = earlier.get(0);
		String oldYearName = masterData.yearNames().get(oldYear.id());

		if (!classRecordFees.isLocked(newYear.id())) {
			throw new CodedConflictException("FEE_STRUCTURE_NOT_FINALISED", null,
					"The fee structure for the new academic year must be finalised first.", null);
		}

		List<StandardInfo> activeStandards = queryActiveStandards();
		StandardInfo currentStandard = activeStandards.stream()
				.filter(s -> s.id() == standardId)
				.findFirst()
				.orElseThrow(() -> new NotFoundException("Standard", standardId));

		Optional<StandardInfo> nextStandard = PromotionRules.nextStandard(currentStandard, activeStandards);

		List<DivisionInfo> divisionsInNewYear = queryDivisions(newYear.id());
		StandardInfo targetForDefault = nextStandard.orElse(currentStandard);
		List<DivisionInfo> targetDivisions = divisionsInNewYear.stream()
				.filter(d -> d.standardId() == targetForDefault.id())
				.toList();

		List<PromoteScreenDto.DivisionRef> divisionRefs = divisionsInNewYear.stream()
				.filter(d -> d.standardId() == targetForDefault.id() || d.standardId() == currentStandard.id())
				.map(d -> new PromoteScreenDto.DivisionRef(d.id(), d.name(), d.standardId()))
				.toList();

		List<PromoteScreenDto.CategoryRef> categoryRefs = masterData.pickLists(newYear.id()).categories().stream()
				.filter(MasterDataLookup.CategoryOption::active)
				.map(c -> new PromoteScreenDto.CategoryRef(c.id(), c.name()))
				.toList();

		List<CandidateStudent> candidates = classRecordPromotion.studentsToPromote(oldYear.id(), newYear.id(), standardId);

		List<PromoteScreenDto.StudentItemDto> studentItems = candidates.stream().map(c -> {
			BigDecimal unpaidFee = feeCarryForward.preview(c.enrollmentId());
			PromotionDefault def = PromotionRules.computeDefault(currentStandard, toCandidate(c), activeStandards, targetDivisions);
			return new PromoteScreenDto.StudentItemDto(
					c.studentId(),
					c.enrollmentId(),
					c.grNo(),
					c.studentName(),
					c.divisionName(),
					c.categoryName(),
					unpaidFee.toPlainString(),
					def.defaultOutcome().name(),
					def.targetDivisionId(),
					def.defaultStream(),
					c.version());
		}).toList();

		PromoteScreenDto.StandardRef nextStandardRef = nextStandard
				.map(s -> new PromoteScreenDto.StandardRef(s.id(), s.name(), s.usesStream()))
				.orElse(null);

		List<String> streamChoices = (nextStandard.isPresent() && nextStandard.get().usesStream())
				? List.of("Science", "Commerce")
				: List.of();

		return new PromoteScreenDto(oldYearName, newYear.name(), nextStandardRef, streamChoices, divisionRefs, categoryRefs, studentItems);
	}

	@Transactional
	public PromoteResult promote(long standardId, PromoteInput input) {
		if (input == null || input.lines() == null || input.lines().isEmpty()) {
			throw new ValidationException("lines", "At least one student line is required.");
		}

		YearRef newYear = masterData.currentYear()
				.orElseThrow(() -> new CodedConflictException("NO_NEW_YEAR_YET", null, "No new academic year has been started yet.", null));

		List<YearDates> earlier = masterData.earlierYears(newYear.startDate());
		if (earlier.isEmpty()) {
			throw new CodedConflictException("NO_NEW_YEAR_YET", null, "No previous academic year exists to promote from.", null);
		}
		YearDates oldYear = earlier.get(0);

		classRecordPromotion.lockStandardStudying(oldYear.id(), standardId);

		if (!classRecordFees.isLocked(newYear.id())) {
			throw new CodedConflictException("FEE_STRUCTURE_NOT_FINALISED", null,
					"The fee structure for the new academic year must be finalised first.", null);
		}

		List<CandidateStudent> candidatesList = classRecordPromotion.studentsToPromote(oldYear.id(), newYear.id(), standardId);
		Map<Long, CandidateStudent> candidateMap = candidatesList.stream()
				.collect(Collectors.toMap(CandidateStudent::enrollmentId, c -> c));

		List<StandardInfo> activeStandards = queryActiveStandards();
		StandardInfo currentStandard = activeStandards.stream()
				.filter(s -> s.id() == standardId)
				.findFirst()
				.orElseThrow(() -> new NotFoundException("Standard", standardId));

		Optional<StandardInfo> nextStandard = PromotionRules.nextStandard(currentStandard, activeStandards);
		List<DivisionInfo> divisionsInNewYear = queryDivisions(newYear.id());

		Set<Long> seenEnrollments = new HashSet<>();
		for (PromoteInput.Line line : input.lines()) {
			if (line.enrollmentId() == null || !seenEnrollments.add(line.enrollmentId())) {
				throw new CodedConflictException("ALREADY_PROCESSED", "lines", "Duplicate student in promotion request.", null);
			}
			CandidateStudent candidate = candidateMap.get(line.enrollmentId());
			if (candidate == null) {
				throw new CodedConflictException("ALREADY_PROCESSED", "lines",
						"One or more students are no longer studying or have already been processed.", null);
			}
			if (line.version() != null && !line.version().equals(candidate.version())) {
				throw new StaleVersionException(candidate.version());
			}
		}

		List<FieldError> fieldErrors = new ArrayList<>();
		for (int i = 0; i < input.lines().size(); i++) {
			PromoteInput.Line line = input.lines().get(i);
			CandidateStudent candidate = candidateMap.get(line.enrollmentId());
			Outcome outcome = Outcome.fromString(line.outcome()).orElse(null);

			Long targetStdId = (outcome == Outcome.PROMOTED)
					? nextStandard.map(StandardInfo::id).orElse(null)
					: (outcome == Outcome.DETAINED ? currentStandard.id() : null);
			Long targetCatId = line.categoryId() != null ? line.categoryId() : candidate.categoryId();

			PromotionDecision decision = new PromotionDecision(line.enrollmentId(), outcome, targetStdId, line.divisionId(), targetCatId, line.stream());
			List<DivisionInfo> targetDivisions = divisionsInNewYear.stream()
					.filter(d -> targetStdId != null && d.standardId() == targetStdId)
					.toList();

			List<String> errors = PromotionRules.validateDecision(currentStandard, toCandidate(candidate), decision, activeStandards, targetDivisions);
			for (String error : errors) {
				fieldErrors.add(new FieldError("lines[" + i + "]", error));
			}
		}

		if (!fieldErrors.isEmpty()) {
			throw new ValidationException(fieldErrors);
		}

		for (PromoteInput.Line line : input.lines()) {
			Outcome outcome = Outcome.fromString(line.outcome()).get();
			if (outcome == Outcome.PROMOTED || outcome == Outcome.DETAINED) {
				long targetStandardId = (outcome == Outcome.PROMOTED)
						? nextStandard.map(StandardInfo::id).orElse(currentStandard.id())
						: currentStandard.id();
				long targetCategoryId = line.categoryId() != null ? line.categoryId() : candidateMap.get(line.enrollmentId()).categoryId();

				if (!classRecordFees.hasFee(newYear.id(), targetStandardId, targetCategoryId)) {
					throw new CodedConflictException("FEE_NOT_SET", null,
							"No fee is set for the target standard and category in the new academic year.", null);
				}
			}
		}

		int promoted = 0;
		int detained = 0;
		int left = 0;
		BigDecimal carriedTotal = new BigDecimal("0.00");
		Long doneBy = currentUser.currentUserId().orElse(1L);

		record ItemExecution(CandidateStudent candidate, PromoteInput.Line line, Outcome outcome,
				Long targetStdId, Long targetCatId) {}

		List<ItemExecution> items = new ArrayList<>();
		for (PromoteInput.Line line : input.lines()) {
			CandidateStudent candidate = candidateMap.get(line.enrollmentId());
			Outcome outcome = Outcome.fromString(line.outcome()).get();
			Long targetStdId = (outcome == Outcome.PROMOTED)
					? nextStandard.map(StandardInfo::id).orElse(null)
					: (outcome == Outcome.DETAINED ? currentStandard.id() : null);
			Long targetCatId = line.categoryId() != null ? line.categoryId() : candidate.categoryId();

			items.add(new ItemExecution(candidate, line, outcome, targetStdId, targetCatId));
			if (outcome == Outcome.PROMOTED) {
				promoted++;
			} else if (outcome == Outcome.DETAINED) {
				detained++;
			} else {
				left++;
			}
		}

		record ItemResult(long studentId, long fromEnrollmentId, Long toEnrollmentId, String outcome,
				Long toStandardId, Long toDivisionId, Long toCategoryId,
				BigDecimal carriedAmount, BigDecimal broughtForwardBefore, BigDecimal broughtForwardAfter) {}

		List<ItemResult> results = new ArrayList<>();
		for (ItemExecution item : items) {
			PromotionItemOutcome pio = new PromotionItemOutcome(
					item.line().enrollmentId(),
					newYear.id(),
					item.outcome().name(),
					item.targetStdId(),
					item.line().divisionId(),
					item.targetCatId(),
					item.line().stream(),
					item.candidate().version());

			Long newEnrollmentId = classRecordPromotion.recordOutcome(pio);

			if (item.outcome() != Outcome.LEFT) {
				CarryForwardResult carry = feeCarryForward.carryForward(
						item.candidate().studentId(), oldYear.id(), newYear.id(), item.line().enrollmentId());
				carriedTotal = carriedTotal.add(carry.carriedAmount());
				results.add(new ItemResult(item.candidate().studentId(), item.line().enrollmentId(),
						newEnrollmentId, item.outcome().name(), item.targetStdId(), item.line().divisionId(),
						item.targetCatId(), carry.carriedAmount(), carry.broughtForwardBefore(), carry.broughtForwardAfter()));
			} else {
				results.add(new ItemResult(item.candidate().studentId(), item.line().enrollmentId(),
						null, "LEFT", null, null, null,
						new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00")));
			}
		}

		PromotionRun run = runs.save(new PromotionRun(oldYear.id(), newYear.id(), standardId, doneBy,
				promoted, detained, left, carriedTotal));

		for (ItemResult res : results) {
			runItems.save(new PromotionRunItem(run.getId(), res.studentId(), res.fromEnrollmentId(),
					res.toEnrollmentId(), res.outcome(), res.toStandardId(), res.toDivisionId(),
					res.toCategoryId(), res.carriedAmount(), res.broughtForwardBefore(), res.broughtForwardAfter()));
		}

		return new PromoteResult(run.getId(), promoted, detained, left, carriedTotal.toPlainString());
	}

	private List<StandardInfo> queryActiveStandards() {
		return masterData.filterOptions().standards().stream()
				.filter(MasterDataLookup.StandardOption::active)
				.map(s -> new StandardInfo(s.id(), s.name(), s.displayOrder(), s.usesStream(), s.active()))
				.toList();
	}

	private List<DivisionInfo> queryDivisions(long yearId) {
		return masterData.pickLists(yearId).divisions().stream()
				.filter(MasterDataLookup.DivisionOption::active)
				.map(d -> new DivisionInfo(d.id(), d.name(), d.standardId()))
				.toList();
	}

	private static PromotionRules.StudentCandidate toCandidate(CandidateStudent c) {
		return new PromotionRules.StudentCandidate(c.enrollmentId(), c.studentId(), c.grNo(),
				c.studentName(), c.divisionId(), c.divisionName(), c.categoryId(), c.stream());
	}

}
