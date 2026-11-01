package com.admin.management.masterdata.api;

import java.time.LocalDate;

/**
 * Creates an academic year record and records master data creation (FR-001).
 */
public interface AcademicYearCreator {

	long createYear(String name, LocalDate startDate, LocalDate endDate);

}
