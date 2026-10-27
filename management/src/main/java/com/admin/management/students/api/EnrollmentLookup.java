package com.admin.management.students.api;

import java.util.List;
import java.util.Optional;

/**
 * The only way other modules read class records (constitution VII, F2 research R2). The fees module uses it to
 * give every class record of a finalised year its fee account.
 */
public interface EnrollmentLookup {

	/** Every class record of the academic year, including those of students who have left, in id order. */
	List<ClassRecordRef> classRecordsOfYear(Long yearId);

	/** The number of class records of the academic year; the same set as {@link #classRecordsOfYear}. */
	long countOfYear(Long yearId);

	/**
	 * The class record the student's screens show: the current academic year's, else the most recent one (the rule
	 * of the Students list). Empty when the student has no class record or does not exist.
	 */
	Optional<ClassRecordRef> shownRecord(Long studentId);

	/** The student's class record of that year, if there is one. */
	Optional<ClassRecordRef> byStudentAndYear(Long studentId, Long yearId);

	record ClassRecordRef(Long enrollmentId, Long studentId, Long yearId, Long standardId, Long categoryId) {
	}

}
