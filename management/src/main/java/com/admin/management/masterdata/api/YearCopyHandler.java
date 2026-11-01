package com.admin.management.masterdata.api;

import java.time.LocalDate;

/**
 * Handler for creating a new academic year with division and fee copy from the latest previous year (FR-001..FR-005).
 */
public interface YearCopyHandler {

	YearCopyResult createWithCopy(String name, LocalDate startDate, LocalDate endDate);

}
