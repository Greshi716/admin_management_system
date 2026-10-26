package com.admin.management.students;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets the browser tests start from an empty student list: students cannot be deleted through the product, and
 * the master data they refer to cannot be cleared while they exist. It also moves a student's class record to
 * another year, which the product cannot do before F4. It exists only under the {@code test} profile (never in a real installation),
 * next to the other test-support endpoint.
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

	/** Gives a student only an earlier-year record: moves the one record to another year and division. */
	@PostMapping("/students/{id}/move-record")
	@Transactional
	ResponseEntity<Void> moveRecord(@PathVariable long id, @RequestParam long academicYearId,
			@RequestParam long divisionId, @RequestParam String yearStartDate) {
		jdbc.update("""
				UPDATE student_enrollments SET academic_year_id = ?, division_id = ?, year_start_date = ?::date
				WHERE student_id = ?
				""", academicYearId, divisionId, yearStartDate, id);
		return ResponseEntity.noContent().build();
	}

}
