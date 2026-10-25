package com.admin.management.students;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets the browser tests start from an empty student list: students cannot be deleted through the product, and
 * the master data they refer to cannot be cleared while they exist. It exists only under the {@code test}
 * profile (never in a real installation), next to the other test-support endpoint.
 */
@RestController
@Profile("test")
@RequestMapping("/api/test-support")
class StudentTestSupportController {

	private final JdbcTemplate jdbc;

	StudentTestSupportController(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@PostMapping("/clear-students")
	@Transactional
	ResponseEntity<Void> clearStudents() {
		jdbc.update("DELETE FROM student_photos");
		jdbc.update("DELETE FROM student_enrollments");
		jdbc.update("DELETE FROM student_gr_numbers");
		jdbc.update("DELETE FROM students");
		return ResponseEntity.noContent().build();
	}

}
