package com.admin.management.shared;

/**
 * What the students module needs from the fees module, defined here so students does not depend on fees (F2 research
 * R2; the same pattern as {@link CurrentAcademicYearProvider}). The fees module provides the real one.
 */
public interface ClassRecordFees {

	/** True when the fee structure of the academic year is finalised, so a class record of it keeps its standard and category. */
	boolean isLocked(Long yearId);

	/**
	 * True when the year is finalised, taking the year's status row for sharing so a running finalise is waited for (and
	 * waits for the caller in turn). For a change of a class record's standard or category; the caller must be in a
	 * transaction, which keeps the lock until it ends.
	 */
	boolean lockedForChange(Long yearId);

	/**
	 * A class record was added. Runs in the caller's transaction after the record is saved and flushed: when the year
	 * is finalised it creates the record's fee account; otherwise it does nothing.
	 */
	void classRecordAdded(Long enrollmentId, Long yearId, Long standardId, Long categoryId);

}
