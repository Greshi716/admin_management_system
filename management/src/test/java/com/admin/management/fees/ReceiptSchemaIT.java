package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.FeeTestData.Setup;
import com.admin.management.support.IntegrationTest;

/**
 * The tables, triggers, views and the developer function of V8__receipts.sql, each rule checked with a rejected or
 * accepted statement (constitution I, II, V). A value with three decimals is not tested here: PostgreSQL rounds it to
 * fit numeric(12,2) instead of refusing it, so that refusal belongs to the service (ReceiptMoneyInputIT). The triggers
 * forbid deleting a receipt, so every test uses students and receipt numbers of its own.
 */
@Covers("FR-001")
@Covers("FR-009")
@Covers("FR-012")
@Covers("FR-022")
@Covers("FR-023")
class ReceiptSchemaIT extends IntegrationTest {

	private static final String CHECK_VIOLATION = "23514";

	private static final String FOREIGN_KEY_VIOLATION = "23503";

	private static final String NUMERIC_OVERFLOW = "22003";

	private static final String UNIQUE_VIOLATION = "23505";

	@Autowired
	JdbcTemplate jdbc;

	Setup setup;

	long adminId;

	int students;

	/** A student with a class record, a fee account of 10,000.00 and a registration fee of 500.00. */
	record Fx(long studentId, long enrollmentId, String grNo) {
	}

	@BeforeAll
	void seed() {
		setup = FeeTestData.year(jdbc, "Receipt", LocalDate.of(2026, 4, 1), 1);
		adminId = jdbc.queryForObject("select id from users order by id limit 1", Long.class);
	}

	Fx student() {
		int n = ++students;
		long enrollmentId = FeeTestData.student(jdbc, setup, 9000 + n, 0, 0);
		long studentId = jdbc.queryForObject("select student_id from student_enrollments where id = ?", Long.class,
				enrollmentId);
		jdbc.update("insert into fee_accounts (enrollment_id, academic_year_id, amount, registration_fee, updated_at)"
				+ " values (?, ?, 10000.00, 500.00, now())", enrollmentId, setup.yearId());
		return new Fx(studentId, enrollmentId, "GR-" + (9000 + n));
	}

	/** Inserts a receipt row with the values given; everything else is valid. Returns the serial no. */
	long receipt(String no, Fx f, String type, boolean withEnrollment, String reg, String fee, String total, String mode,
			String chequeNo, String bank, LocalDate chequeDate) {
		return jdbc.queryForObject("""
				insert into receipts (receipt_no, receipt_date, academic_year_id, student_id, enrollment_id, student_name,
				    gr_no, receipt_type, registration_paid, fee_paid, total, total_words, payment_mode, cheque_no,
				    bank_name, cheque_date, created_by, created_at)
				values (?, date '2026-10-01', ?, ?, ?, 'Test Student', ?, ?, ?, ?, ?, 'Words only', ?, ?, ?, ?, ?, now())
				returning serial_no
				""", Long.class, no, setup.yearId(), f.studentId(), withEnrollment ? f.enrollmentId() : null, f.grNo(),
				type, new BigDecimal(reg), new BigDecimal(fee), new BigDecimal(total), mode, chequeNo, bank,
				chequeDate == null ? null : Date.valueOf(chequeDate), adminId);
	}

	long cash(String no, Fx f, String reg, String fee) {
		String total = new BigDecimal(reg).add(new BigDecimal(fee)).toPlainString();
		return receipt(no, f, "CURRENT_YEAR", true, reg, fee, total, "CASH", null, null, null);
	}

	long cheque(String no, Fx f, String fee) {
		return receipt(no, f, "CURRENT_YEAR", true, "0.00", fee, fee, "CHEQUE", "CQ" + no, "Test Bank",
				LocalDate.of(2026, 10, 1));
	}

	long previous(String no, Fx f, String fee, String mode) {
		boolean cheque = mode.equals("CHEQUE");
		return receipt(no, f, "PREVIOUS_YEARS", false, "0.00", fee, fee, mode, cheque ? "CQ" + no : null,
				cheque ? "Test Bank" : null, cheque ? LocalDate.of(2026, 10, 1) : null);
	}

	long mark(long serialNo, String kind, String reason, String amount) {
		return jdbc.queryForObject("""
				insert into receipt_marks (receipt_serial_no, kind, reason, amount_added_back, marked_by, marked_at)
				values (?, ?, ?, ?, ?, now()) returning id
				""", Long.class, serialNo, kind, reason, new BigDecimal(amount),
				kind.equals("DEVELOPER_VOID") ? null : adminId);
	}

	SQLException refused(Runnable statement) {
		DataAccessException ex = assertThrows(DataAccessException.class, statement::run);
		Throwable root = ex;
		while (root.getCause() != null) {
			root = root.getCause();
		}
		return (SQLException) root;
	}

	void assertRefused(String sqlState, String text, Runnable statement) {
		SQLException ex = refused(statement);
		assertThat(ex.getSQLState()).as(ex.getMessage()).isEqualTo(sqlState);
		assertThat(ex.getMessage()).contains(text);
	}

	// ------------------------------------------------------------------------------------------- receipts

	@Test
	void aValidReceiptKeepsItsAmountsWithTwoDecimals() {
		Fx f = student();
		long serial = cash("S-100", f, "500", "10500.5");

		Map<String, Object> row = jdbc.queryForMap("select * from receipts where serial_no = ?", serial);

		assertThat((BigDecimal) row.get("registration_paid")).isEqualTo(new BigDecimal("500.00"));
		assertThat((BigDecimal) row.get("fee_paid")).isEqualTo(new BigDecimal("10500.50"));
		assertThat((BigDecimal) row.get("total")).isEqualTo(new BigDecimal("11000.50"));
	}

	@Test
	void theSerialNoIsTheSystemsRunningNumber() {
		Fx f = student();
		long first = cash("S-110", f, "0", "1");
		long second = cash("S-111", f, "0", "1");

		assertThat(second).isGreaterThan(first);
	}

	@Test
	void theTotalMustEqualTheSumAndBeAboveZero() {
		Fx f = student();
		assertRefused(CHECK_VIOLATION, "ck_receipts_total",
				() -> receipt("T-1", f, "CURRENT_YEAR", true, "100", "200", "299.99", "CASH", null, null, null));
		assertRefused(CHECK_VIOLATION, "ck_receipts_total",
				() -> receipt("T-2", f, "CURRENT_YEAR", true, "0", "0", "0", "CASH", null, null, null));
	}

	@Test
	void aNegativeAmountIsRefused() {
		Fx f = student();
		assertRefused(CHECK_VIOLATION, "ck_receipts_amounts",
				() -> receipt("N-1", f, "CURRENT_YEAR", true, "-1", "11", "10", "CASH", null, null, null));
		assertRefused(CHECK_VIOLATION, "ck_receipts_amounts",
				() -> receipt("N-2", f, "CURRENT_YEAR", true, "11", "-1", "10", "CASH", null, null, null));
	}

	@Test
	void aValueTooBigForTwelveDigitsIsRefused() {
		Fx f = student();
		assertRefused(NUMERIC_OVERFLOW, "numeric field overflow",
				() -> receipt("B-1", f, "CURRENT_YEAR", true, "0", "10000000000.00", "10000000000.00", "CASH", null, null,
						null));
	}

	@Test
	void theTypeDecidesWhichColumnsAreAllowed() {
		Fx f = student();
		assertRefused(CHECK_VIOLATION, "ck_receipts_current",
				() -> receipt("Y-1", f, "PREVIOUS_YEARS", false, "10", "10", "20", "CASH", null, null, null));
		assertRefused(CHECK_VIOLATION, "ck_receipts_current",
				() -> receipt("Y-2", f, "PREVIOUS_YEARS", true, "0", "10", "10", "CASH", null, null, null));
		assertRefused(CHECK_VIOLATION, "ck_receipts_current",
				() -> receipt("Y-3", f, "PREVIOUS_YEARS", false, "10", "0", "10", "CASH", null, null, null));
		assertRefused(CHECK_VIOLATION, "ck_receipts_current",
				() -> receipt("Y-4", f, "CURRENT_YEAR", false, "0", "10", "10", "CASH", null, null, null));
		// an unknown type breaks ck_receipts_type and ck_receipts_current together; PostgreSQL names one of them
		assertThat(refused(() -> receipt("Y-5", f, "OTHER", true, "0", "10", "10", "CASH", null, null, null))
			.getSQLState()).isEqualTo(CHECK_VIOLATION);
		assertThat(previous("Y-6", f, "10", "CASH")).isPositive();
	}

	@Test
	void theModeDecidesWhichChequeFieldsAreRequired() {
		Fx f = student();
		LocalDate day = LocalDate.of(2026, 10, 1);
		assertRefused(CHECK_VIOLATION, "ck_receipts_mode",
				() -> receipt("M-1", f, "CURRENT_YEAR", true, "0", "10", "10", "CASH", "CQ1", null, null));
		assertRefused(CHECK_VIOLATION, "ck_receipts_mode",
				() -> receipt("M-2", f, "CURRENT_YEAR", true, "0", "10", "10", "CASH", null, "Bank", null));
		assertRefused(CHECK_VIOLATION, "ck_receipts_mode",
				() -> receipt("M-3", f, "CURRENT_YEAR", true, "0", "10", "10", "CHEQUE", null, "Bank", day));
		assertRefused(CHECK_VIOLATION, "ck_receipts_mode",
				() -> receipt("M-4", f, "CURRENT_YEAR", true, "0", "10", "10", "CHEQUE", "CQ1", "  ", day));
		assertRefused(CHECK_VIOLATION, "ck_receipts_mode",
				() -> receipt("M-5", f, "CURRENT_YEAR", true, "0", "10", "10", "CHEQUE", "CQ1", "Bank", null));
		assertRefused(CHECK_VIOLATION, "ck_receipts_mode",
				() -> receipt("M-6", f, "CURRENT_YEAR", true, "0", "10", "10", "UPI", null, null, null));
		assertThat(cheque("M-7", f, "10")).isPositive();
		assertThat(cash("M-8", f, "0", "10")).isPositive();
	}

	@Test
	void theReceiptNumberIsOneToTwentyCharactersWithoutPadding() {
		Fx f = student();
		assertRefused(CHECK_VIOLATION, "ck_receipts_receipt_no", () -> cash("123456789012345678901", f, "0", "1"));
		assertRefused(CHECK_VIOLATION, "ck_receipts_receipt_no", () -> cash(" P-1", f, "0", "1"));
		assertRefused(CHECK_VIOLATION, "ck_receipts_receipt_no", () -> cash("P-2 ", f, "0", "1"));
		assertRefused(CHECK_VIOLATION, "ck_receipts_receipt_no", () -> cash("", f, "0", "1"));
		assertThat(cash("12345678901234567890", f, "0", "1")).isPositive();
	}

	@Test
	void everyForeignKeyIsChecked() {
		Fx f = student();
		Fx missing = new Fx(f.studentId() + 100_000, f.enrollmentId(), f.grNo());
		assertRefused(FOREIGN_KEY_VIOLATION, "fk_receipts_student", () -> cash("F-1", missing, "0", "1"));
		Fx noEnrollment = new Fx(f.studentId(), f.enrollmentId() + 100_000, f.grNo());
		assertRefused(FOREIGN_KEY_VIOLATION, "fk_receipts_enrollment", () -> cash("F-2", noEnrollment, "0", "1"));
	}

	// ------------------------------------------------------------------------------------------ the marks

	@Test
	void aChequeReceiptCanBeMarkedOnceOnly() {
		Fx f = student();
		long serial = cheque("K-1", f, "5000");

		assertThat(mark(serial, "CHEQUE_NOT_CLEARED", "Returned by the bank", "5000")).isPositive();
		assertRefused(UNIQUE_VIOLATION, "uq_receipt_marks_receipt",
				() -> mark(serial, "CHEQUE_NOT_CLEARED", "Again", "5000"));
		assertRefused(UNIQUE_VIOLATION, "uq_receipt_marks_receipt", () -> mark(serial, "DEVELOPER_VOID", "Wrong", "5000"));
	}

	@Test
	void theReasonIsOneToTwoHundredCharacters() {
		Fx f = student();
		long serial = cheque("K-2", f, "100");
		assertRefused(CHECK_VIOLATION, "ck_receipt_marks_reason", () -> mark(serial, "CHEQUE_NOT_CLEARED", "   ", "100"));
		assertRefused(CHECK_VIOLATION, "ck_receipt_marks_reason",
				() -> mark(serial, "CHEQUE_NOT_CLEARED", "x".repeat(201), "100"));
		assertThat(mark(serial, "CHEQUE_NOT_CLEARED", "x".repeat(200), "100")).isPositive();
	}

	@Test
	void aCashReceiptCannotBeMarkedChequeNotCleared() {
		Fx f = student();
		long serial = cash("K-3", f, "0", "100");
		assertRefused(CHECK_VIOLATION, "only a Cheque receipt",
				() -> mark(serial, "CHEQUE_NOT_CLEARED", "Not a cheque", "100"));
	}

	@Test
	void theAmountAddedBackIsTheReceiptTotal() {
		Fx f = student();
		long serial = cheque("K-4", f, "100");
		assertRefused(CHECK_VIOLATION, "must equal the receipt total",
				() -> mark(serial, "CHEQUE_NOT_CLEARED", "Wrong amount", "99.99"));
		// zero is refused by the trigger (it runs before ck_receipt_marks_amount, which would refuse it too)
		assertRefused(CHECK_VIOLATION, "must equal the receipt total",
				() -> mark(serial, "CHEQUE_NOT_CLEARED", "Zero", "0"));
	}

	@Test
	void aMarkNeedsAnExistingReceiptAndKnownKind() {
		Fx f = student();
		long serial = cheque("K-5", f, "100");
		assertRefused(FOREIGN_KEY_VIOLATION, "fk_receipt_marks_receipt",
				() -> mark(serial + 100_000, "CHEQUE_NOT_CLEARED", "Missing", "100"));
		assertRefused(CHECK_VIOLATION, "ck_receipt_marks_kind", () -> mark(serial, "OTHER", "Unknown kind", "100"));
	}

	// ------------------------------------------------------------------------------------------ immutable

	@Test
	void aReceiptAndAMarkCannotBeUpdatedOrDeleted() {
		Fx f = student();
		long serial = cheque("I-1", f, "700");
		long markId = mark(serial, "CHEQUE_NOT_CLEARED", "Returned", "700");
		Map<String, Object> before = jdbc.queryForMap("select * from receipts where serial_no = ?", serial);
		Map<String, Object> markBefore = jdbc.queryForMap("select * from receipt_marks where id = ?", markId);

		assertRefused("P0001", "append-only: UPDATE",
				() -> jdbc.update("update receipts set fee_paid = 1, total = 1 where serial_no = ?", serial));
		assertRefused("P0001", "append-only: UPDATE", () -> jdbc.update("update receipts set receipt_no = 'X'"));
		assertRefused("P0001", "append-only: DELETE", () -> jdbc.update("delete from receipts where serial_no = ?", serial));
		assertRefused("P0001", "append-only: UPDATE",
				() -> jdbc.update("update receipt_marks set reason = 'changed' where id = ?", markId));
		assertRefused("P0001", "append-only: DELETE", () -> jdbc.update("delete from receipt_marks where id = ?", markId));

		assertThat(jdbc.queryForMap("select * from receipts where serial_no = ?", serial)).isEqualTo(before);
		assertThat(jdbc.queryForMap("select * from receipt_marks where id = ?", markId)).isEqualTo(markBefore);
	}

	@Test
	void aTruncateIsRefused() {
		Fx f = student();
		long serial = cheque("I-2", f, "800");
		long markId = mark(serial, "CHEQUE_NOT_CLEARED", "Returned", "800");

		assertRefused("P0001", "append-only: TRUNCATE", () -> jdbc.execute("truncate table receipt_marks"));
		// receipts is referenced by receipt_marks, so PostgreSQL itself refuses before the trigger would; either way it fails
		refused(() -> jdbc.execute("truncate table receipts"));

		assertThat(jdbc.queryForObject("select count(*) from receipts where serial_no = ?", Integer.class, serial))
			.isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from receipt_marks where id = ?", Integer.class, markId))
			.isEqualTo(1);
	}

	// ------------------------------------------------------------------------------- the receipt number

	@Test
	void aReceiptNumberCannotBeUsedTwice() {
		Fx f = student();
		Fx g = student();
		cash("D-1", f, "0", "10");

		SQLException ex = refused(() -> cash("D-1", g, "0", "10"));

		assertThat(ex.getSQLState()).isEqualTo(UNIQUE_VIOLATION);
		assertThat(ex.getMessage()).contains("constraint \"uq_receipts_receipt_no\"");
		assertThat(jdbc.queryForObject("select count(*) from receipts where receipt_no = 'D-1'", Integer.class))
			.isEqualTo(1);
	}

	@Test
	void theNumberIsComparedIgnoringCase() {
		Fx f = student();
		cash("ab-12", f, "0", "10");

		assertRefused(UNIQUE_VIOLATION, "uq_receipts_receipt_no", () -> cash("AB-12", f, "0", "10"));
		assertRefused(UNIQUE_VIOLATION, "uq_receipts_receipt_no", () -> cash("Ab-12", f, "0", "10"));
	}

	@Test
	void numbersWithAndWithoutLeadingZerosAreDifferentReceipts() {
		Fx f = student();

		assertThat(cash("3100", f, "0", "10")).isPositive();
		assertThat(cash("03100", f, "0", "10")).isPositive();
	}

	@Test
	void aChequeNotClearedReceiptKeepsItsNumber() {
		Fx f = student();
		long serial = cheque("D-2", f, "50");
		mark(serial, "CHEQUE_NOT_CLEARED", "Returned", "50");

		assertRefused(UNIQUE_VIOLATION, "uq_receipts_receipt_no", () -> cheque("D-2", f, "50"));
	}

	@Test
	void aVoidedReceiptFreesItsNumber() {
		Fx f = student();
		long serial = cash("D-3", f, "0", "75");
		jdbc.queryForList("select void_receipt(?, 'Wrong student', 'test developer')", serial);

		long corrected = cash("D-3", f, "0", "75");

		assertThat(corrected).isGreaterThan(serial);
		assertThat(jdbc.queryForObject("select count(*) from receipts where receipt_no = 'D-3'", Integer.class))
			.isEqualTo(2);
		assertRefused(UNIQUE_VIOLATION, "uq_receipts_receipt_no", () -> cash("D-3", f, "0", "75"));
	}

	@Test
	void simultaneousSavesOfOneNumberLetOnlyOneThrough() throws Exception {
		int threads = 6;
		List<Fx> fixtures = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			fixtures.add(student());
		}
		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger saved = new AtomicInteger();
		AtomicInteger refusedCount = new AtomicInteger();
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		List<Future<?>> all = new ArrayList<>();
		for (Fx f : fixtures) {
			all.add(pool.submit(() -> {
				start.await();
				try {
					cash("RACE-1", f, "0", "10");
					saved.incrementAndGet();
				}
				catch (DataAccessException ex) {
					refusedCount.incrementAndGet();
				}
				return null;
			}));
		}
		start.countDown();
		for (Future<?> future : all) {
			future.get(30, TimeUnit.SECONDS);
		}
		pool.shutdown();

		assertThat(saved.get()).isEqualTo(1);
		assertThat(refusedCount.get()).isEqualTo(threads - 1);
		assertThat(jdbc.queryForObject("select count(*) from receipts where receipt_no = 'RACE-1'", Integer.class))
			.isEqualTo(1);
	}

	// ------------------------------------------------------------------------------------------- the views

	@Test
	void theValidViewLeavesOutMarkedReceipts() {
		Fx f = student();
		long valid = cash("V-1", f, "0", "100");
		long marked = cheque("V-2", f, "200");
		mark(marked, "CHEQUE_NOT_CLEARED", "Returned", "200");

		List<Long> serials = jdbc.queryForList("select serial_no from v_valid_receipts where student_id = ?", Long.class,
				f.studentId());

		assertThat(serials).containsExactly(valid);
	}

	@Test
	void thePositionIsWorkedOutFromTheValidReceipts() {
		Fx f = student();

		Map<String, Object> none = position(f);
		assertThat((BigDecimal) none.get("paid_year_fee")).isEqualByComparingTo("0");
		assertThat((BigDecimal) none.get("pending_year_fee")).isEqualByComparingTo("10000.00");
		assertThat((BigDecimal) none.get("pending_registration")).isEqualByComparingTo("500.00");
		assertThat((BigDecimal) none.get("excess_paid")).isEqualByComparingTo("0");

		cash("W-1", f, "100", "3000");
		long returned = cheque("W-2", f, "2000");
		mark(returned, "CHEQUE_NOT_CLEARED", "Returned", "2000");

		Map<String, Object> some = position(f);
		assertThat((BigDecimal) some.get("paid_year_fee")).isEqualByComparingTo("3000.00");
		assertThat((BigDecimal) some.get("paid_registration")).isEqualByComparingTo("100.00");
		assertThat((BigDecimal) some.get("pending_year_fee")).isEqualByComparingTo("7000.00");
		assertThat((BigDecimal) some.get("pending_registration")).isEqualByComparingTo("400.00");
		assertThat((BigDecimal) some.get("excess_paid")).isEqualByComparingTo("0");
	}

	@Test
	void aRevisedFeeBelowThePaidAmountShowsExcessAndZeroPending() {
		Fx f = student();
		cash("W-3", f, "500", "6000");

		jdbc.update("update fee_accounts set amount = 4500.00, registration_fee = 400.00 where enrollment_id = ?",
				f.enrollmentId());

		Map<String, Object> row = position(f);
		assertThat((BigDecimal) row.get("pending_year_fee")).isEqualByComparingTo("0");
		assertThat((BigDecimal) row.get("pending_registration")).isEqualByComparingTo("0");
		assertThat((BigDecimal) row.get("excess_paid")).isEqualByComparingTo("1600.00");
	}

	@Test
	void aFeeNotSetAndNoRegistrationFeeGiveNullPendingAndZeroRegistration() {
		Fx f = student();
		jdbc.update("update fee_accounts set amount = null, registration_fee = null where enrollment_id = ?",
				f.enrollmentId());

		Map<String, Object> row = position(f);

		assertThat(row.get("pending_year_fee")).isNull();
		assertThat((BigDecimal) row.get("pending_registration")).isEqualByComparingTo("0");
	}

	@Test
	void theEarlierPendingIsTheBroughtForwardAmountMinusValidPreviousYearsReceipts() {
		Fx f = student();
		jdbc.update("insert into fee_brought_forward (student_id, amount, updated_at) values (?, 3000.00, now())",
				f.studentId());

		Map<String, Object> none = jdbc.queryForMap("select * from v_earlier_pending where student_id = ?", f.studentId());
		assertThat((BigDecimal) none.get("pending")).isEqualByComparingTo("3000.00");

		previous("E-1", f, "1000", "CASH");
		long returned = previous("E-2", f, "500", "CHEQUE");
		mark(returned, "CHEQUE_NOT_CLEARED", "Returned", "500");
		cash("E-3", f, "0", "900"); // a current-year receipt does not count here

		Map<String, Object> row = jdbc.queryForMap("select * from v_earlier_pending where student_id = ?", f.studentId());
		assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo("3000.00");
		assertThat((BigDecimal) row.get("paid")).isEqualByComparingTo("1000.00");
		assertThat((BigDecimal) row.get("pending")).isEqualByComparingTo("2000.00");
	}

	@Test
	void aStudentWithNoBroughtForwardHasNoEarlierPendingRow() {
		Fx f = student();

		assertThat(jdbc.queryForObject("select count(*) from v_earlier_pending where student_id = ?", Integer.class,
				f.studentId()))
			.isZero();
	}

	Map<String, Object> position(Fx f) {
		return jdbc.queryForMap("select * from v_fee_position where enrollment_id = ?", f.enrollmentId());
	}

	// ------------------------------------------------------------------------------------ void_receipt

	@Test
	void theDeveloperVoidAddsAMarkAndAnAuditRowAndChangesNothingElse() {
		Fx f = student();
		long serial = cash("X-1", f, "100", "900");
		Map<String, Object> before = jdbc.queryForMap("select * from receipts where serial_no = ?", serial);

		jdbc.queryForList("select void_receipt(?, '  TEST-1: wrong student  ', ' test developer ')", serial);

		Map<String, Object> mark = jdbc.queryForMap("select * from receipt_marks where receipt_serial_no = ?", serial);
		assertThat(mark.get("kind")).isEqualTo("DEVELOPER_VOID");
		assertThat(mark.get("reason")).isEqualTo("TEST-1: wrong student");
		assertThat(mark.get("marked_by")).isNull();
		assertThat((BigDecimal) mark.get("amount_added_back")).isEqualByComparingTo("1000.00");

		Map<String, Object> audit = jdbc.queryForMap(
				"select area, action, entity, user_id, reason, changes::text as changes from audit_log where entity = ?",
				"Receipt#" + serial);
		assertThat(audit.get("area")).isEqualTo("Fees");
		assertThat(audit.get("action")).isEqualTo("Edit");
		assertThat(audit.get("user_id")).isNull();
		assertThat(audit.get("reason")).isEqualTo("TEST-1: wrong student");
		String changes = (String) audit.get("changes");
		assertThat(changes).contains("\"VALID\"").contains("\"VOIDED\"").contains("test developer")
			.contains("1000.00");

		assertThat(jdbc.queryForMap("select * from receipts where serial_no = ?", serial)).isEqualTo(before);
		assertThat(jdbc.queryForObject("select count(*) from v_valid_receipts where serial_no = ?", Integer.class, serial))
			.isZero();
	}

	@Test
	void theDeveloperVoidRefusesWhatItCannotDoSafely() {
		Fx f = student();
		long serial = cash("X-2", f, "0", "100");

		assertRefused("P0001", "a reason is required", () -> voidIt(serial, "  ", "dev"));
		assertRefused("P0001", "a reason is required", () -> voidIt(serial, null, "dev"));
		assertRefused("P0001", "at most 200 characters", () -> voidIt(serial, "x".repeat(201), "dev"));
		assertRefused("P0001", "the name of the person", () -> voidIt(serial, "Wrong", " "));
		assertRefused("P0001", "the name of the person", () -> voidIt(serial, "Wrong", null));
		assertRefused("P0001", "no receipt with serial no.", () -> voidIt(serial + 100_000, "Wrong", "dev"));

		assertThat(jdbc.queryForObject("select count(*) from receipt_marks where receipt_serial_no = ?", Integer.class,
				serial))
			.isZero();
		assertThat(jdbc.queryForObject("select count(*) from audit_log where entity = ?", Integer.class,
				"Receipt#" + serial))
			.isZero();

		voidIt(serial, "Wrong student", "dev");
		assertRefused("P0001", "already marked", () -> voidIt(serial, "Again", "dev"));
		assertThat(jdbc.queryForObject("select count(*) from audit_log where entity = ?", Integer.class,
				"Receipt#" + serial))
			.isEqualTo(1);
	}

	@Test
	void aChequeNotClearedReceiptCannotBeVoidedAfterwards() {
		Fx f = student();
		long serial = cheque("X-3", f, "100");
		mark(serial, "CHEQUE_NOT_CLEARED", "Returned", "100");

		assertRefused("P0001", "already marked", () -> voidIt(serial, "Wrong", "dev"));
	}

	void voidIt(long serial, String reason, String by) {
		jdbc.queryForList("select void_receipt(?, ?, ?)", serial, reason, by);
	}

}
