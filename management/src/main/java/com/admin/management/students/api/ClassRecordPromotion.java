package com.admin.management.students.api;

import java.util.List;

/**
 * Public interface for reading candidates to promote and writing promotion outcomes in the students module
 * (FR-006, FR-008, FR-010, FR-011, FR-014, FR-016, FR-018, FR-019).
 */
public interface ClassRecordPromotion {

	/**
	 * Locks the old year's Studying enrollments for the given standard FOR UPDATE.
	 */
	void lockStandardStudying(long academicYearId, long standardId);

	/**
	 * Reads active students in {@code standardId} who are still 'Studying' in {@code fromYearId}
	 * and do not have an enrollment record in {@code toYearId}.
	 */
	List<CandidateStudent> studentsToPromote(long fromYearId, long toYearId, long standardId);

	/**
	 * Reads counts of active students still 'Studying' in {@code fromYearId} with no record in {@code toYearId},
	 * grouped by standard, for the year change warning.
	 */
	List<PendingStandard> pendingByStandard(long fromYearId, long toYearId);

	/**
	 * Records a student's outcome:
	 * - Updates the old enrollment year_status to PROMOTED, DETAINED, or LEFT.
	 * - For PROMOTED and DETAINED: creates a new enrollment in the target year, triggers fee account creation.
	 * - For LEFT: sets students.status to 'Left'.
	 *
	 * @return the new enrollment ID (null if outcome is LEFT)
	 */
	Long recordOutcome(PromotionItemOutcome outcome);

	record CandidateStudent(long enrollmentId, long studentId, String grNo, String studentName,
			long divisionId, String divisionName, long categoryId, String categoryName,
			String stream, long version) {
	}

	record PendingStandard(long standardId, String name, int pending) {
	}

	record PromotionItemOutcome(long fromEnrollmentId, long toYearId, String outcome,
			Long toStandardId, Long toDivisionId, Long toCategoryId, String stream, long version) {
	}

}
