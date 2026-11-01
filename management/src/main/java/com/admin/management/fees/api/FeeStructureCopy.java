package com.admin.management.fees.api;

/**
 * Public interface for copying fee structure rows to a new academic year (FR-001, FR-002, FR-005).
 */
public interface FeeStructureCopy {

	/**
	 * Copies fee rows from {@code fromYearId} to {@code toYearId}, shifting due dates forward by one year,
	 * creates the draft fee year status, and records the Fees audit entry.
	 *
	 * @param fromYearId the source academic year ID
	 * @param toYearId   the destination academic year ID
	 * @return the count of fee rows copied
	 */
	int copyFeeStructure(long fromYearId, long toYearId);

}
