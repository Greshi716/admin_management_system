package com.admin.management.students.api;

/** The only way other modules ask whether a student exists (constitution VII). */
public interface StudentLookup {

	/** True when a student with the id exists, whatever the status. False for null. */
	boolean exists(Long studentId);

}
