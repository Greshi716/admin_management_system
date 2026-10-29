package com.admin.management.masterdata;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.admin.management.masterdata.academicyear.AcademicYear;
import com.admin.management.masterdata.academicyear.AcademicYearRepository;
import com.admin.management.masterdata.api.MasterDataLookup;
import com.admin.management.masterdata.category.StudentCategory;
import com.admin.management.masterdata.category.StudentCategoryRepository;
import com.admin.management.masterdata.division.Division;
import com.admin.management.masterdata.division.DivisionRepository;
import com.admin.management.masterdata.standard.Standard;
import com.admin.management.masterdata.standard.StandardRepository;
import com.admin.management.shared.web.NotFoundException;

/** Implements {@link MasterDataLookup} over the master data repositories. Reads every time; nothing is cached. */
@Service
class MasterDataLookupService implements MasterDataLookup {

	private final AcademicYearRepository years;

	private final StandardRepository standards;

	private final DivisionRepository divisions;

	private final StudentCategoryRepository categories;

	private final Clock clock;

	MasterDataLookupService(AcademicYearRepository years, StandardRepository standards, DivisionRepository divisions,
			StudentCategoryRepository categories, Clock clock) {
		this.years = years;
		this.standards = standards;
		this.divisions = divisions;
		this.categories = categories;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<YearRef> currentYear() {
		LocalDate today = LocalDate.now(clock);
		return years.findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqual(today, today)
			.map((year) -> new YearRef(year.getId(), year.getName(), year.getStartDate()));
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<LocalDate> yearStartDate(Long yearId) {
		return yearId == null ? Optional.empty() : years.findById(yearId).map(AcademicYear::getStartDate);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<YearDates> yearDates(Long yearId) {
		return yearId == null ? Optional.empty() : years.findById(yearId).map(MasterDataLookupService::dates);
	}

	@Override
	@Transactional(readOnly = true)
	public Map<Long, YearDates> yearDatesByYear() {
		Map<Long, YearDates> result = new LinkedHashMap<>();
		for (AcademicYear year : years.findAllByOrderByStartDateDesc()) {
			result.put(year.getId(), dates(year));
		}
		return result;
	}

	@Override
	@Transactional(readOnly = true)
	public List<YearDates> earlierYears(LocalDate beforeStart) {
		if (beforeStart == null) {
			return List.of();
		}
		return years.findAllByOrderByStartDateDesc()
			.stream()
			.filter((year) -> year.getStartDate().isBefore(beforeStart))
			.map(MasterDataLookupService::dates)
			.toList();
	}

	private static YearDates dates(AcademicYear year) {
		return new YearDates(year.getId(), year.getName(), year.getStartDate(), year.getEndDate());
	}

	@Override
	@Transactional(readOnly = true)
	public boolean divisionBelongsTo(Long divisionId, Long yearId, Long standardId) {
		if (divisionId == null || yearId == null || standardId == null) {
			return false;
		}
		return divisions.findById(divisionId)
			.map((division) -> division.getAcademicYear().getId().equals(yearId)
					&& division.getStandard().getId().equals(standardId))
			.orElse(false);
	}

	@Override
	@Transactional(readOnly = true)
	public boolean isStandardActive(Long standardId) {
		return standardId != null && standards.findById(standardId).map(Standard::isActive).orElse(false);
	}

	@Override
	@Transactional(readOnly = true)
	public boolean isDivisionActive(Long divisionId) {
		return divisionId != null && divisions.findById(divisionId).map(Division::isActive).orElse(false);
	}

	@Override
	@Transactional(readOnly = true)
	public boolean isCategoryActive(Long categoryId) {
		return categoryId != null && categories.findById(categoryId).map(StudentCategory::isActive).orElse(false);
	}

	@Override
	@Transactional(readOnly = true)
	public Map<Long, String> yearNames() {
		return toMap(years.findAll(), AcademicYear::getId, AcademicYear::getName);
	}

	@Override
	@Transactional(readOnly = true)
	public Map<Long, String> standardNames() {
		return toMap(standards.findAll(), Standard::getId, Standard::getName);
	}

	@Override
	@Transactional(readOnly = true)
	public Map<Long, Integer> standardDisplayOrders() {
		return toMap(standards.findAll(), Standard::getId, Standard::getDisplayOrder);
	}

	@Override
	@Transactional(readOnly = true)
	public Map<Long, String> divisionNames() {
		return toMap(divisions.findAll(), Division::getId, Division::getName);
	}

	@Override
	@Transactional(readOnly = true)
	public Map<Long, String> categoryNames() {
		return toMap(categories.findAll(), StudentCategory::getId, StudentCategory::getName);
	}

	@Override
	@Transactional(readOnly = true)
	public PickLists pickLists(Long yearId, Long keepStandardId, Long keepDivisionId, Long keepCategoryId) {
		if (yearId == null || !years.existsById(yearId)) {
			throw new NotFoundException("Academic year", yearId);
		}
		List<StandardOption> standardOptions = standards.findAll()
			.stream()
			.filter((standard) -> standard.isActive() || standard.getId().equals(keepStandardId))
			.sorted(Comparator.comparingInt(Standard::getDisplayOrder).thenComparing(Standard::getName))
			.map((standard) -> new StandardOption(standard.getId(), standard.getName(), standard.getDisplayOrder(),
					standard.getStream(), standard.isActive()))
			.toList();
		List<DivisionOption> divisionOptions = divisions.findAllByYear(yearId)
			.stream()
			.filter((division) -> division.isActive() || division.getId().equals(keepDivisionId))
			.sorted(Comparator.comparingInt((Division division) -> division.getStandard().getDisplayOrder())
				.thenComparing((Division division) -> division.getStandard().getName())
				.thenComparing(Division::getName))
			.map((division) -> new DivisionOption(division.getId(), division.getName(), division.getStandard().getId(),
					division.isActive()))
			.toList();
		List<CategoryOption> categoryOptions = categories.findAll()
			.stream()
			.filter((category) -> category.isActive() || category.getId().equals(keepCategoryId))
			.sorted(Comparator.comparing(StudentCategory::getId))
			.map((category) -> new CategoryOption(category.getId(), category.getCode(), category.getName(),
					category.isActive()))
			.toList();
		return new PickLists(standardOptions, divisionOptions, categoryOptions);
	}

	@Override
	@Transactional(readOnly = true)
	public FilterOptions filterOptions() {
		List<StandardOption> standardOptions = standards.findAll()
			.stream()
			.sorted(Comparator.comparingInt(Standard::getDisplayOrder).thenComparing(Standard::getName))
			.map((standard) -> new StandardOption(standard.getId(), standard.getName(), standard.getDisplayOrder(),
					standard.getStream(), standard.isActive()))
			.toList();
		List<YearDivisionOption> divisionOptions = years.findAll()
			.stream()
			.sorted(Comparator.comparing(AcademicYear::getStartDate).reversed())
			.flatMap((year) -> divisions.findAllByYear(year.getId())
				.stream()
				.sorted(Comparator.comparingInt((Division division) -> division.getStandard().getDisplayOrder())
					.thenComparing((Division division) -> division.getStandard().getName())
					.thenComparing(Division::getName))
				.map((division) -> new YearDivisionOption(division.getId(), division.getName(),
						division.getStandard().getId(), year.getId(), year.getName(), division.isActive())))
			.toList();
		List<CategoryOption> categoryOptions = categories.findAll()
			.stream()
			.sorted(Comparator.comparing(StudentCategory::getId))
			.map((category) -> new CategoryOption(category.getId(), category.getCode(), category.getName(),
					category.isActive()))
			.toList();
		return new FilterOptions(standardOptions, divisionOptions, categoryOptions);
	}

	private static <T, V> Map<Long, V> toMap(List<T> rows, Function<T, Long> id, Function<T, V> value) {
		Map<Long, V> map = new LinkedHashMap<>();
		rows.forEach((row) -> map.put(id.apply(row), value.apply(row)));
		return map;
	}

}
