package com.admin.management.students.api;

import java.util.Optional;

/** The only way other modules ask about a student (constitution VII). */
public interface StudentLookup {

	/** True when a student with the id exists, whatever the status. False for null. */
	boolean exists(Long studentId);

	/** The name, GR number and status of the student, or empty when there is no such student or the id is null. */
	Optional<StudentSummary> summary(Long studentId);

	/** {@code fullName} is surname, first name, middle name; {@code status} is Active or Left. */
	record StudentSummary(Long id, String fullName, String grNo, String status) {
	}

}
