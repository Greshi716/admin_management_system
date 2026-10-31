package com.admin.management.fees;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.admin.management.shared.web.ValidationException;

/**
 * Lets the browser tests start from empty fee tables: fee accounts and brought-forward amounts cannot be deleted
 * through the product, and while they exist the students, class records, standards, categories and years they refer
 * to cannot be cleared (every foreign key is RESTRICT). A test that clears students or master data calls this first.
 * It exists only under the {@code test} profile (never in a real installation), next to the other test-support
 * endpoints.
 */
@RestController
@Profile("test")
@RequestMapping("/api/test-support")
class FeeTestSupportController {

	/** Most receipts one call may make; the 10,000 receipt scenarios are the largest user. */
	static final int MAX_SEED_RECEIPTS = 20_000;

	private final JdbcTemplate jdbc;

	FeeTestSupportController(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@PostMapping("/clear-fees")
	@Transactional
	ResponseEntity<Void> clearFees() {
		jdbc.update("DELETE FROM fee_accounts");
		jdbc.update("DELETE FROM fee_brought_forward");
		jdbc.update("DELETE FROM fee_rows");
		jdbc.update("DELETE FROM fee_year_status");
		return ResponseEntity.noContent().build();
	}

	/**
	 * Empties the receipt tables. The product cannot do this (append-only triggers, constitution II), so the
	 * triggers are switched off for this one transaction ({@code session_replication_role = replica}; the test
	 * database user is a superuser). Call it before clear-students and before clearing master data.
	 */
	@PostMapping("/clear-receipts")
	@Transactional
	ResponseEntity<Void> clearReceipts() {
		jdbc.execute("SET LOCAL session_replication_role = replica");
		jdbc.update("DELETE FROM receipt_marks");
		jdbc.update("DELETE FROM receipts");
		return ResponseEntity.noContent().build();
	}

	/**
	 * Makes {@code count} synthetic cash receipts (numbers {@code <prefix>-1} and so on) of {@code feePaid} each, spread
	 * round-robin over the class records of the year, for the performance scenarios. It does not check what is due.
	 */
	@PostMapping("/seed-receipts")
	@Transactional
	ResponseEntity<Map<String, Object>> seedReceipts(@RequestParam long academicYearId, @RequestParam int count,
			@RequestParam String receiptDate, @RequestParam(defaultValue = "SEEDR") String prefix,
			@RequestParam(defaultValue = "100.00") BigDecimal feePaid) {
		if (count < 1 || count > MAX_SEED_RECEIPTS) {
			throw new ValidationException("count", "The count must be from 1 to " + MAX_SEED_RECEIPTS + ".");
		}
		int created = jdbc.update("""
				insert into receipts (receipt_no, receipt_date, academic_year_id, student_id, enrollment_id, student_name,
				    gr_no, standard_id, division_id, category_id, receipt_type, registration_paid, fee_paid, total,
				    total_words, payment_mode, created_by, created_at)
				select ?::text || '-' || g.n, ?::date, e.academic_year_id, e.student_id, e.id,
				    s.surname || ' ' || s.first_name, s.gr_no, e.standard_id, e.division_id, e.category_id,
				    'CURRENT_YEAR', 0, ?, ?, 'Seed receipt', 'CASH', (select min(id) from users), now()
				from generate_series(1, ?) as g(n)
				join (select r.*, row_number() over (order by r.id) as rn, count(*) over () as cnt
				      from student_enrollments r where r.academic_year_id = ?) e on e.rn = ((g.n - 1) % e.cnt) + 1
				join students s on s.id = e.student_id
				""", prefix, receiptDate, feePaid, feePaid, count, academicYearId);
		return ResponseEntity.ok(Map.of("created", created));
	}

	/** Sets the amount brought forward from earlier years for a student (inserts or replaces it). */
	@PostMapping("/students/{id}/brought-forward")
	@Transactional
	ResponseEntity<Void> setBroughtForward(@PathVariable long id, @RequestParam BigDecimal amount) {
		jdbc.update("""
				insert into fee_brought_forward (student_id, amount, updated_at) values (?, ?, now())
				on conflict (student_id) do update set amount = excluded.amount, updated_at = now(),
				    version = fee_brought_forward.version + 1
				""", id, amount);
		return ResponseEntity.noContent().build();
	}

}
