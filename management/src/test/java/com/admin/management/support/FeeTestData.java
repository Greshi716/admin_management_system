package com.admin.management.support;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Synthetic fee data for tests (constitution VI): a year with standards and divisions, fee rows, a year status and
 * class records in bulk. Writes with plain SQL so it works before the fee services exist and stays fast for the
 * 2,000 and 10,000 record tests.
 */
public final class FeeTestData {

	/** One year with its standards (numbered from 1, in display order), a division A of each, and the seeded categories. */
	public record Setup(long yearId, LocalDate yearStart, List<Long> standardIds, Map<Long, Long> divisionByStandard,
			List<Long> categoryIds) {

		public long standard(int index) {
			return standardIds.get(index);
		}

		public long category(int index) {
			return categoryIds.get(index);
		}

	}

	private FeeTestData() {
	}

	/**
	 * Creates an academic year from {@code start} for one year and {@code standards} standards named
	 * "Fee Std 1" and so on, with a division A for each. Names must not exist yet, so give each call its own
	 * {@code namePrefix}.
	 */
	public static Setup year(JdbcTemplate jdbc, String namePrefix, LocalDate start, int standards) {
		long yearId = jdbc.queryForObject(
				"insert into academic_years (name, start_date, end_date) values (?, ?, ?) returning id", Long.class,
				namePrefix + " " + start.getYear() + "-" + (start.getYear() + 1), Date.valueOf(start),
				Date.valueOf(start.plusYears(1).minusDays(1)));
		List<Long> standardIds = new ArrayList<>();
		Map<Long, Long> divisions = new LinkedHashMap<>();
		for (int s = 1; s <= standards; s++) {
			long standardId = jdbc.queryForObject(
					"insert into standards (name, display_order) values (?, ?) returning id", Long.class,
					namePrefix + " Std " + s, s);
			standardIds.add(standardId);
			divisions.put(standardId, jdbc.queryForObject(
					"insert into divisions (academic_year_id, standard_id, name) values (?, ?, 'A') returning id",
					Long.class, yearId, standardId));
		}
		List<Long> categories = jdbc.queryForList("select id from student_categories order by id", Long.class);
		return new Setup(yearId, start, standardIds, divisions, categories);
	}

	/** Adds a fee row. {@code categoryId}, {@code registrationFee} and {@code due} may be null. Returns the row id. */
	public static long addRow(JdbcTemplate jdbc, long yearId, long standardId, Long categoryId, String amount,
			String registrationFee, LocalDate due) {
		return jdbc.queryForObject("""
				insert into fee_rows (academic_year_id, standard_id, category_id, amount, registration_fee, due_date)
				values (?, ?, ?, ?, ?, ?) returning id
				""", Long.class, yearId, standardId, categoryId, new BigDecimal(amount),
				registrationFee == null ? null : new BigDecimal(registrationFee), due == null ? null : Date.valueOf(due));
	}

	/** Gives the year a status row: Draft, or Finalised with the time of the call. Does not create any account. */
	public static void setStatus(JdbcTemplate jdbc, long yearId, boolean finalised) {
		jdbc.update("delete from fee_year_status where academic_year_id = ?", yearId);
		jdbc.update("insert into fee_year_status (academic_year_id, status, finalised_at) values (?, ?, "
				+ (finalised ? "now()" : "null") + ")", yearId, finalised ? "Finalised" : "Draft");
	}

	/**
	 * Registers student number {@code n} (GR No. {@code GR-n}) in the year, in the standard and category with the
	 * given indexes of the setup. Returns the id of the class record.
	 */
	public static long student(JdbcTemplate jdbc, Setup setup, int n, int standardIndex, int categoryIndex) {
		long standardId = setup.standard(standardIndex);
		long categoryId = setup.category(categoryIndex);
		long studentId = StudentTestData.insertStudent(jdbc, StudentTestData.valid(n, categoryId, standardId));
		StudentTestData.insertEnrollment(jdbc, studentId, new StudentTestData.YearSetup(setup.yearId(),
				setup.yearStart(), standardId, setup.divisionByStandard().get(standardId)), categoryId);
		return jdbc.queryForObject("select id from student_enrollments where student_id = ? and academic_year_id = ?",
				Long.class, studentId, setup.yearId());
	}

	/**
	 * Inserts {@code count} students (GR No. {@code prefix-1} to {@code prefix-count}) with one class record each in
	 * the year, spread evenly over the standards and the categories of the setup. Uses set-based SQL, so 10,000
	 * records take a second or two. Returns the number of class records made.
	 */
	public static int seedClassRecords(JdbcTemplate jdbc, Setup setup, String prefix, int count) {
		String standardCase = caseOf("standard", setup.standardIds());
		String categoryCase = caseOf("category", setup.categoryIds());
		String sql = """
				with created as (
				    insert into students (gr_no, surname, first_name, gender, dob, mobile_1, address, category_id,
				        admission_date, admission_standard_id)
				    select ? || '-' || g.n, 'Seedsurname' || g.n, 'Seed', case when g.n %% 2 = 0 then 'Male' else 'Female' end,
				        date '2012-01-01', '9' || lpad(g.n::text, 9, '0'), 'Seed address ' || g.n || ', Test Town',
				        %s, date '2020-06-01', %s
				    from (select n, n %% %d as standard, n %% %d as category from generate_series(1, ?) as s(n)) g
				    returning id, gr_no, category_id, admission_standard_id
				), keys as (
				    insert into student_gr_numbers (gr_key, student_id, assigned_at)
				    select lower(gr_no), id, now() from created
				)
				insert into student_enrollments (student_id, academic_year_id, standard_id, division_id, category_id,
				    year_start_date)
				select id, ?, admission_standard_id,
				    (select d.id from divisions d where d.academic_year_id = ? and d.standard_id = created.admission_standard_id
				     and d.name = 'A'),
				    category_id, ?
				from created
				""".formatted(categoryCase, standardCase, setup.standardIds().size(), setup.categoryIds().size());
		return jdbc.update(sql, prefix, count, setup.yearId(), setup.yearId(), Date.valueOf(setup.yearStart()));
	}

	/** A synthetic student with a class record and a fee account (see {@link #studentWithAccount}). */
	public record Fx(long studentId, long enrollmentId, String grNo) {
	}

	/**
	 * Registers student {@code n} (standard and category index 0) and gives the class record a fee account of
	 * {@code amount} and {@code registrationFee} (null for none). Receipts cannot be deleted, so each test class uses
	 * student numbers and receipt numbers of its own.
	 */
	public static Fx studentWithAccount(JdbcTemplate jdbc, Setup setup, int n, String amount, String registrationFee) {
		long enrollmentId = student(jdbc, setup, n, 0, 0);
		long studentId = jdbc.queryForObject("select student_id from student_enrollments where id = ?", Long.class,
				enrollmentId);
		jdbc.update("insert into fee_accounts (enrollment_id, academic_year_id, amount, registration_fee, updated_at)"
				+ " values (?, ?, ?, ?, now())", enrollmentId, setup.yearId(), new BigDecimal(amount),
				registrationFee == null ? null : new BigDecimal(registrationFee));
		return new Fx(studentId, enrollmentId, "GR-" + n);
	}

	/** The id of the first user (the test admin), who is the creator of seeded receipts. */
	public static long adminId(JdbcTemplate jdbc) {
		return jdbc.queryForObject("select id from users order by id limit 1", Long.class);
	}

	/** Inserts a valid current-year cash receipt with a unique synthetic number; returns the serial no. */
	public static long cashReceipt(JdbcTemplate jdbc, Setup setup, Fx student, String receiptNo, LocalDate date,
			String registrationPaid, String feePaid) {
		BigDecimal registration = new BigDecimal(registrationPaid);
		BigDecimal fee = new BigDecimal(feePaid);
		return jdbc.queryForObject("""
				insert into receipts (receipt_no, receipt_date, academic_year_id, student_id, enrollment_id, student_name,
				    gr_no, receipt_type, registration_paid, fee_paid, total, total_words, payment_mode, created_by,
				    created_at)
				values (?, ?, ?, ?, ?, 'Test Student', ?, 'CURRENT_YEAR', ?, ?, ?, 'Test words', 'CASH', ?, now())
				returning serial_no
				""", Long.class, receiptNo, Date.valueOf(date), setup.yearId(), student.studentId(),
				student.enrollmentId(), student.grNo(), registration, fee, registration.add(fee), adminId(jdbc));
	}

	/** Inserts a valid current-year cheque receipt (year fee only); returns the serial no. */
	public static long chequeReceipt(JdbcTemplate jdbc, Setup setup, Fx student, String receiptNo, LocalDate date,
			String feePaid) {
		BigDecimal fee = new BigDecimal(feePaid);
		return jdbc.queryForObject("""
				insert into receipts (receipt_no, receipt_date, academic_year_id, student_id, enrollment_id, student_name,
				    gr_no, receipt_type, registration_paid, fee_paid, total, total_words, payment_mode, cheque_no,
				    bank_name, cheque_date, created_by, created_at)
				values (?, ?, ?, ?, ?, 'Test Student', ?, 'CURRENT_YEAR', 0, ?, ?, 'Test words', 'CHEQUE', ?, 'Test Bank',
				    ?, ?, now())
				returning serial_no
				""", Long.class, receiptNo, Date.valueOf(date), setup.yearId(), student.studentId(),
				student.enrollmentId(), student.grNo(), fee, fee, "CQ" + receiptNo, Date.valueOf(date), adminId(jdbc));
	}

	/** Marks a receipt "Cheque not cleared" with plain SQL (the service comes later); returns the mark id. */
	public static long markNotCleared(JdbcTemplate jdbc, long serialNo, String reason) {
		return jdbc.queryForObject("""
				insert into receipt_marks (receipt_serial_no, kind, reason, amount_added_back, marked_by, marked_at)
				select serial_no, 'CHEQUE_NOT_CLEARED', ?, total, ?, now() from receipts where serial_no = ?
				returning id
				""", Long.class, reason, adminId(jdbc), serialNo);
	}

	/**
	 * Creates {@code students} synthetic students with a class record and a fee account of 36,500.00 each, then
	 * {@code receipts} cash receipts of 1.00 spread over them (the receipts do not check what is due). Two thousand
	 * students and ten thousand receipts take a few seconds.
	 */
	public static void bulkStudentsAndReceipts(JdbcTemplate jdbc, Setup setup, String prefix, int students,
			int receipts) {
		seedClassRecords(jdbc, setup, prefix, students);
		jdbc.update("""
				insert into fee_accounts (enrollment_id, academic_year_id, amount, updated_at)
				select e.id, e.academic_year_id, 36500.00, now() from student_enrollments e
				join students s on s.id = e.student_id
				where e.academic_year_id = ? and s.gr_no like ?
				""", setup.yearId(), prefix + "-%");
		jdbc.update("""
				insert into receipts (receipt_no, receipt_date, academic_year_id, student_id, enrollment_id, student_name,
				    gr_no, standard_id, division_id, category_id, receipt_type, registration_paid, fee_paid, total,
				    total_words, payment_mode, created_by, created_at)
				select ? || '-R' || g.n, date '2026-10-01', e.academic_year_id, e.student_id, e.id,
				    s.surname || ' ' || s.first_name, s.gr_no, e.standard_id, e.division_id, e.category_id,
				    'CURRENT_YEAR', 0, 1.00, 1.00, 'Seed receipt', 'CASH', ?, now()
				from generate_series(1, ?) as g(n)
				join (select r.*, row_number() over (order by r.id) as rn, count(*) over () as cnt
				      from student_enrollments r join students st on st.id = r.student_id
				      where r.academic_year_id = ? and st.gr_no like ?) e on e.rn = ((g.n - 1) % e.cnt) + 1
				join students s on s.id = e.student_id
				""", prefix, adminId(jdbc), receipts, setup.yearId(), prefix + "-%");
	}

	/**
	 * Inserts a receipt for the register tests with the class kept on the receipt (standard 1 / division A named
	 * "Reg Std 1"). {@code type} is CURRENT_YEAR or PREVIOUS_YEARS; a previous-years receipt has no class record.
	 * A CHEQUE receipt gets a cheque number, bank and date. Returns the serial no.
	 */
	public static long registerReceipt(JdbcTemplate jdbc, Setup setup, Fx student, String receiptNo, LocalDate date,
			String mode, String type, String registrationPaid, String feePaid) {
		BigDecimal registration = new BigDecimal(registrationPaid);
		BigDecimal fee = new BigDecimal(feePaid);
		boolean cheque = "CHEQUE".equals(mode);
		boolean current = "CURRENT_YEAR".equals(type);
		return jdbc.queryForObject("""
				insert into receipts (receipt_no, receipt_date, academic_year_id, student_id, enrollment_id, student_name,
				    gr_no, standard_id, standard_name, division_name, receipt_type, registration_paid, fee_paid, total,
				    total_words, payment_mode, cheque_no, bank_name, cheque_date, created_by, created_at)
				values (?, ?, ?, ?, ?, 'Register Student', ?, ?, 'Reg Std 1', 'A', ?, ?, ?, ?, 'Test words', ?, ?, ?, ?, ?,
				    now())
				returning serial_no
				""", Long.class, receiptNo, Date.valueOf(date), setup.yearId(), student.studentId(),
				current ? student.enrollmentId() : null, student.grNo(), setup.standard(0), type, registration, fee,
				registration.add(fee), mode, cheque ? "CQ" + receiptNo : null, cheque ? "Test Bank" : null,
				cheque ? Date.valueOf(date) : null, adminId(jdbc));
	}

	/** Paid amounts of a class record, worked out in plain Java from the receipts and marks (not from the views). */
	public record Balance(BigDecimal paidYearFee, BigDecimal paidRegistration, BigDecimal pendingYearFee,
			BigDecimal pendingRegistration) {
	}

	/**
	 * The independent check of the balance (SC-002): sums the valid current-year receipts of the class record in Java
	 * and subtracts them from the account. A receipt with a mark does not count. Pending is never below zero.
	 */
	public static Balance balance(JdbcTemplate jdbc, long enrollmentId) {
		BigDecimal fee = BigDecimal.ZERO;
		BigDecimal registration = BigDecimal.ZERO;
		for (Map<String, Object> row : jdbc.queryForList("""
				select r.fee_paid, r.registration_paid from receipts r
				where r.enrollment_id = ? and r.receipt_type = 'CURRENT_YEAR'
				  and not exists (select 1 from receipt_marks m where m.receipt_serial_no = r.serial_no)
				""", enrollmentId)) {
			fee = fee.add((BigDecimal) row.get("fee_paid"));
			registration = registration.add((BigDecimal) row.get("registration_paid"));
		}
		Map<String, Object> account = jdbc.queryForMap(
				"select amount, registration_fee from fee_accounts where enrollment_id = ?", enrollmentId);
		BigDecimal due = account.get("amount") == null ? BigDecimal.ZERO : (BigDecimal) account.get("amount");
		BigDecimal registrationDue = account.get("registration_fee") == null ? BigDecimal.ZERO
				: (BigDecimal) account.get("registration_fee");
		return new Balance(fee, registration, due.subtract(fee).max(BigDecimal.ZERO),
				registrationDue.subtract(registration).max(BigDecimal.ZERO));
	}

	/** Sets uses_stream on a standard (FR-012, FR-040). */
	public static void setUsesStream(JdbcTemplate jdbc, long standardId, boolean usesStream) {
		jdbc.update("update standards set uses_stream = ? where id = ?", usesStream, standardId);
	}

	/** Sets stream on an enrollment record (FR-012, FR-040). */
	public static void setStream(JdbcTemplate jdbc, long enrollmentId, String stream) {
		jdbc.update("update student_enrollments set stream = ? where id = ?", stream, enrollmentId);
	}

	/** {@code case g.<column> when 0 then id0 when 1 then id1 ... end}: ids are our own numbers, never user input. */
	private static String caseOf(String column, List<Long> ids) {
		StringBuilder sql = new StringBuilder("case g.").append(column);
		for (int i = 0; i < ids.size(); i++) {
			sql.append(" when ").append(i).append(" then ").append(ids.get(i));
		}
		return sql.append(" end").toString();
	}

}
