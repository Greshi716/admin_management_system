package com.admin.management.masterdata.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The only way other modules read master data (constitution VII, research R7). Nothing is cached, so a rename shows
 * at once. "Active" checks are for new choices: a record that already holds an inactive value keeps it, and the
 * maps below always include inactive entries so such a record can still be shown.
 */
public interface MasterDataLookup {

	/** The academic year that contains today in the school's time zone, or empty. */
	Optional<YearRef> currentYear();

	/** The start date of an academic year, or empty when there is no such year. */
	Optional<LocalDate> yearStartDate(Long yearId);

	/** Whether the division exists, belongs to the academic year and belongs to the standard. */
	boolean divisionBelongsTo(Long divisionId, Long yearId, Long standardId);

	/** False when the standard does not exist or is inactive. */
	boolean isStandardActive(Long standardId);

	/** False when the division does not exist or is inactive. */
	boolean isDivisionActive(Long divisionId);

	/** False when the category does not exist or is inactive. */
	boolean isCategoryActive(Long categoryId);

	Map<Long, String> yearNames();

	Map<Long, String> standardNames();

	Map<Long, Integer> standardDisplayOrders();

	Map<Long, String> divisionNames();

	Map<Long, String> categoryNames();

	/**
	 * The choices for a form: active standards and categories, and the active divisions of the academic year. A
	 * value that is not active is still included when its id is given (the value an existing record holds).
	 *
	 * @throws com.admin.management.shared.web.NotFoundException when the academic year does not exist
	 */
	PickLists pickLists(Long yearId, Long keepStandardId, Long keepDivisionId, Long keepCategoryId);

	/**
	 * Every standard, category and division of every academic year, active or not, for list filters: a record keeps
	 * the values it was saved with, so a filter must be able to reach them.
	 */
	FilterOptions filterOptions();

	default PickLists pickLists(Long yearId) {
		return pickLists(yearId, null, null, null);
	}

	record YearRef(Long id, String name, LocalDate startDate) {
	}

	record StandardOption(Long id, String name, int displayOrder, String stream, boolean active) {
	}

	record DivisionOption(Long id, String name, Long standardId, boolean active) {
	}

	record YearDivisionOption(Long id, String name, Long standardId, Long yearId, String yearName, boolean active) {
	}

	record CategoryOption(Long id, String code, String name, boolean active) {
	}

	/** Standards in display order, divisions by year (newest first), standard order then name, categories in creation order. */
	record FilterOptions(List<StandardOption> standards, List<YearDivisionOption> divisions,
			List<CategoryOption> categories) {
	}

	/** Standards in display order, divisions by standard order then name, categories in creation order. */
	record PickLists(List<StandardOption> standards, List<DivisionOption> divisions, List<CategoryOption> categories) {
	}

}
