package com.admin.management.students;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.admin.management.masterdata.api.MasterDataLookup;
import com.admin.management.shared.web.ValidationException;

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

	/** Most class records one call may make; the 10,000 student list test is the largest user. */
	static final int MAX_SEED = 20_000;

	private final JdbcTemplate jdbc;

	private final MasterDataLookup masterData;

	StudentTestSupportController(JdbcTemplate jdbc, MasterDataLookup masterData) {
		this.jdbc = jdbc;
		this.masterData = masterData;
	}

	/**
	 * Makes {@code count} synthetic students (GR No. {@code <grPrefix>-1} and so on) with one class record each in the
	 * given year, standard and division, for the browser tests and the performance scenarios. Categories are spread
	 * evenly over all categories unless {@code categoryId} is given. One statement, so a clash (for example a GR No.
	 * that is already used) leaves nothing behind.
	 */
	@PostMapping("/seed-class-records")
	@Transactional
	ResponseEntity<Map<String, Object>> seedClassRecords(@RequestParam long academicYearId,
			@RequestParam long standardId, @RequestParam long divisionId, @RequestParam int count,
			@RequestParam(required = false) Long categoryId, @RequestParam(defaultValue = "SEED") String grPrefix) {
		if (count < 1 || count > MAX_SEED) {
			throw new ValidationException("count", "The count must be from 1 to " + MAX_SEED + ".");
		}
		LocalDate yearStart = masterData.yearStartDate(academicYearId)
			.orElseThrow(() -> new ValidationException("academicYearId", "There is no such academic year."));
		if (!masterData.divisionBelongsTo(divisionId, academicYearId, standardId)) {
			throw new ValidationException("divisionId", "The division must belong to the year and the standard.");
		}
		List<Long> categories = masterData.categoryNames().keySet().stream().sorted().toList();
		if (categoryId != null) {
			if (!categories.contains(categoryId)) {
				throw new ValidationException("categoryId", "There is no such category.");
			}
			categories = List.of(categoryId);
		}
		// The ids are numbers read from the database, never text from the request, so they are safe to write into the SQL.
		String categoryArray = categories.stream().map(String::valueOf).collect(Collectors.joining(","));
		int created = new NamedParameterJdbcTemplate(jdbc).update("""
				with created as (
				    insert into students (gr_no, surname, first_name, gender, dob, mobile_1, address, category_id,
				        admission_date, admission_standard_id)
				    select :prefix || '-' || g.n, 'Seedsurname' || g.n, 'Seed',
				        case when g.n %% 2 = 0 then 'Male' else 'Female' end, date '2012-01-01',
				        '9' || lpad(g.n::text, 9, '0'), 'Seed address ' || g.n || ', Test Town',
				        (array[%s])[1 + (g.n %% %d)], date '2020-06-01', :standard
				    from generate_series(1, :count) as g(n)
				    returning id, gr_no, category_id
				), keys as (
				    insert into student_gr_numbers (gr_key, student_id, assigned_at)
				    select lower(gr_no), id, now() from created
				)
				insert into student_enrollments (student_id, academic_year_id, standard_id, division_id, category_id,
				    year_start_date)
				select id, :year, :standard, :division, category_id, :start from created
				""".formatted(categoryArray, categories.size()), new MapSqlParameterSource().addValue("prefix", grPrefix)
			.addValue("standard", standardId)
			.addValue("count", count)
			.addValue("year", academicYearId)
			.addValue("division", divisionId)
			.addValue("start", java.sql.Date.valueOf(yearStart)));
		return ResponseEntity.ok(Map.of("created", created));
	}

	@PostMapping("/clear-students")
	@Transactional
	ResponseEntity<Void> clearStudents() {
		Integer feeAccounts = jdbc.queryForObject(
				"SELECT count(*) FROM fee_accounts fa JOIN student_enrollments se ON fa.enrollment_id = se.id",
				Integer.class);
		Integer feeBroughtForward = jdbc.queryForObject(
				"SELECT count(*) FROM fee_brought_forward fbf JOIN students s ON fbf.student_id = s.id",
				Integer.class);
		if ((feeAccounts != null && feeAccounts > 0) || (feeBroughtForward != null && feeBroughtForward > 0)) {
			throw new org.springframework.web.server.ResponseStatusException(
					org.springframework.http.HttpStatus.CONFLICT,
					"fee accounts or brought-forward records still point at students");
		}
		jdbc.execute("SET LOCAL session_replication_role = replica");
		jdbc.update("DELETE FROM admission_requests");
		jdbc.update("DELETE FROM admission_import_batches");
		jdbc.update("DELETE FROM promotion_corrections");
		jdbc.update("DELETE FROM promotion_run_items");
		jdbc.update("DELETE FROM promotion_runs");
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
