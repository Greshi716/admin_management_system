package com.admin.management.masterdata.api;

/**
 * Public interface for copying divisions from a previous academic year to a new academic year (FR-001, FR-004).
 */
public interface DivisionCopy {

	/**
	 * Copies active divisions belonging to {@code fromYearId} into {@code toYearId}.
	 *
	 * @param fromYearId the source academic year ID
	 * @param toYearId   the destination academic year ID
	 * @return the count of divisions copied
	 */
	int copyDivisions(long fromYearId, long toYearId);

}
