package com.admin.management.masterdata.academicyear;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.admin.management.masterdata.MasterDataAudit;
import com.admin.management.masterdata.Versions;
import com.admin.management.shared.CurrentAcademicYearProvider;
import com.admin.management.shared.web.ConflictException;
import com.admin.management.shared.web.NotFoundException;
import com.admin.management.shared.web.ValidationException;

/**
 * Academic years: create, edit, delete (FR-010, FR-011, FR-020) and the current year (FR-012, FR-013). The current
 * year is the one containing today's date in the school's time zone; nothing stores it and there is no fallback.
 */
@Service
public class AcademicYearService implements CurrentAcademicYearProvider, com.admin.management.masterdata.api.AcademicYearCreator {

	static final String TYPE = "AcademicYear";

	private static final DateTimeFormatter SHOWN = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH);

	private final AcademicYearRepository years;

	private final MasterDataAudit audit;

	private final Clock clock;

	AcademicYearService(AcademicYearRepository years, MasterDataAudit audit, Clock clock) {
		this.years = years;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<AcademicYearDto> list() {
		LocalDate today = LocalDate.now(clock);
		return years.findAllByOrderByStartDateDesc().stream().map((year) -> AcademicYearDto.of(year, today)).toList();
	}

	@Transactional(readOnly = true)
	public AcademicYearDto get(Long id) {
		return AcademicYearDto.of(find(id), LocalDate.now(clock));
	}

	@Override
	@Transactional
	public long createYear(String name, LocalDate startDate, LocalDate endDate) {
		String trimmed = name.trim();
		checkDates(startDate, endDate, -1);
		AcademicYear year = years.saveAndFlush(new AcademicYear(trimmed, startDate, endDate));
		audit.created(TYPE, year.getId(), year.snapshot());
		return year.getId();
	}

	public AcademicYearDto create(AcademicYearDto input) {
		String name = input.name().trim();
		checkDates(input.startDate(), input.endDate(), -1);
		AcademicYear year = years.saveAndFlush(new AcademicYear(name, input.startDate(), input.endDate()));
		audit.created(TYPE, year.getId(), year.snapshot());
		return AcademicYearDto.of(year, LocalDate.now(clock));
	}

	@Transactional
	public AcademicYearDto update(Long id, AcademicYearDto input) {
		AcademicYear year = find(id);
		LocalDate today = LocalDate.now(clock);
		Versions.check(input.version(), year.getVersion(), () -> AcademicYearDto.of(year, today));
		checkDates(input.startDate(), input.endDate(), id);
		var before = year.snapshot();
		year.change(input.name().trim(), input.startDate(), input.endDate());
		years.saveAndFlush(year);
		audit.edited(TYPE, id, before, year.snapshot());
		return AcademicYearDto.of(year, today);
	}

	/** Deletes a year nothing refers to; a referenced year is refused as "in use" by the database (FR-020). */
	@Transactional
	public void delete(Long id) {
		AcademicYear year = find(id);
		var values = year.snapshot();
		years.delete(year);
		years.flush();
		audit.deleted(TYPE, id, values);
	}

	/** The name of the year that contains today, or null when today is in no year (FR-013). */
	@Override
	@Transactional(readOnly = true)
	public String currentName() {
		LocalDate today = LocalDate.now(clock);
		return years.findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqual(today, today)
			.map(AcademicYear::getName)
			.orElse(null);
	}

	private void checkDates(LocalDate start, LocalDate end, long ownId) {
		if (!end.isAfter(start)) {
			throw new ValidationException("endDate", "The end date must be after the start date.");
		}
		years.findOverlapping(start, end, ownId).stream().findFirst().ifPresent((other) -> {
			throw new ConflictException("startDate", "These dates overlap the academic year " + other.getName() + " ("
					+ other.getStartDate().format(SHOWN) + " to " + other.getEndDate().format(SHOWN) + ").");
		});
	}

	private AcademicYear find(Long id) {
		return years.findById(id).orElseThrow(() -> new NotFoundException("Academic year", id));
	}

}
