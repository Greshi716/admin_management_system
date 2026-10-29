package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.admin.management.fees.Receipt.Draft;
import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.FeeTestData.Setup;
import com.admin.management.support.IntegrationTest;

/**
 * The mapping of the receipt tables, the immutability seen from Hibernate, the school header properties and the audit
 * writer for receipts (constitution II, III, V). The database side of immutability is in ReceiptSchemaIT.
 */
@Covers("FR-010")
@Covers("FR-009")
@Covers("FR-017")
@Covers("FR-021")
class ReceiptRepositoryIT extends IntegrationTest {

	@Autowired
	ReceiptRepository receipts;

	@Autowired
	ReceiptMarkRepository marks;

	@Autowired
	FeesAudit feesAudit;

	@Autowired
	SchoolProperties school;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PlatformTransactionManager transactionManager;

	TransactionTemplate tx;

	Setup setup;

	long adminId;

	long studentId;

	long enrollmentId;

	int numbers;

	@BeforeAll
	void seed() {
		tx = new TransactionTemplate(transactionManager);
		setup = FeeTestData.year(jdbc, "RepoR", LocalDate.of(2026, 4, 1), 1);
		adminId = jdbc.queryForObject("select id from users order by id limit 1", Long.class);
		enrollmentId = FeeTestData.student(jdbc, setup, 7001, 0, 0);
		studentId = jdbc.queryForObject("select student_id from student_enrollments where id = ?", Long.class,
				enrollmentId);
		jdbc.update("insert into fee_accounts (enrollment_id, academic_year_id, amount, registration_fee, updated_at)"
				+ " values (?, ?, 10500.00, 500.00, now())", enrollmentId, setup.yearId());
	}

	Draft cash(String no, String registration, String fee) {
		return new Draft(no, LocalDate.of(2026, 10, 1), setup.yearId(), studentId, enrollmentId, "Test Student",
				"GR-7001", setup.standardIds().get(0), setup.divisionByStandard().get(setup.standardIds().get(0)), null,
				"Std", "A", Receipt.Type.CURRENT_YEAR, new BigDecimal(registration), new BigDecimal(fee), "Words",
				Receipt.Mode.CASH, null, null, null, adminId, Instant.parse("2026-10-01T05:00:00Z"));
	}

	Draft cheque(String no, String fee) {
		return new Draft(no, LocalDate.of(2026, 10, 1), setup.yearId(), studentId, enrollmentId, "Test Student",
				"GR-7001", null, null, null, null, null, Receipt.Type.CURRENT_YEAR, BigDecimal.ZERO, new BigDecimal(fee),
				"Words", Receipt.Mode.CHEQUE, "CQ1", "Test Bank", LocalDate.of(2026, 10, 1), adminId,
				Instant.parse("2026-10-01T05:00:00Z"));
	}

	String nextNo() {
		return "RR-" + (++numbers);
	}

	@Test
	void aReceiptRoundTripsWithTwoDecimalsAndAnAssignedSerialNo() {
		Receipt saved = receipts.saveAndFlush(new Receipt(cash(nextNo(), "500", "10000")));

		assertThat(saved.getSerialNo()).isNotNull();
		Receipt loaded = receipts.findById(saved.getSerialNo()).orElseThrow();
		assertThat(loaded.getRegistrationPaid()).isEqualTo(new BigDecimal("500.00"));
		assertThat(loaded.getFeePaid()).isEqualTo(new BigDecimal("10000.00"));
		assertThat(loaded.getTotal()).isEqualTo(new BigDecimal("10500.00"));
		assertThat(jdbc.queryForObject("select total from receipts where serial_no = ?", BigDecimal.class,
				saved.getSerialNo()).toPlainString()).isEqualTo("10500.00");
		assertThat(loaded.getReceiptType()).isEqualTo(Receipt.Type.CURRENT_YEAR);
		assertThat(loaded.getPaymentMode()).isEqualTo(Receipt.Mode.CASH);
	}

	@Test
	void aSecondSaveOfALoadedReceiptChangesNothingAndACopyChangeIsIgnored() throws Exception {
		String no = nextNo();
		Receipt saved = receipts.saveAndFlush(new Receipt(cash(no, "0", "1000")));
		Receipt loaded = receipts.findById(saved.getSerialNo()).orElseThrow();

		receipts.saveAndFlush(loaded);
		tx.executeWithoutResult(status -> {
			Receipt managed = receipts.findById(saved.getSerialNo()).orElseThrow();
			java.lang.reflect.Field field;
			try {
				field = Receipt.class.getDeclaredField("studentName");
				field.setAccessible(true);
				field.set(managed, "Changed");
			}
			catch (ReflectiveOperationException ex) {
				throw new IllegalStateException(ex);
			}
		});

		assertThat(jdbc.queryForObject("select student_name from receipts where serial_no = ?", String.class,
				saved.getSerialNo())).isEqualTo("Test Student");
		assertThat(receipts.findByReceiptNoIgnoreCase(no.toLowerCase())).hasSize(1);
	}

	@Test
	void theDatabaseStillRefusesAChangeThatBypassesHibernate() {
		Receipt saved = receipts.saveAndFlush(new Receipt(cash(nextNo(), "0", "1000")));

		assertThatThrownBy(() -> jdbc.update("update receipts set student_name = 'X' where serial_no = ?",
				saved.getSerialNo()))
			.isInstanceOf(DataAccessException.class)
			.hasMessageContaining("append-only");
	}

	@Test
	void aMarkRoundTripsAndOnlyOneIsAllowedPerReceipt() {
		Receipt saved = receipts.saveAndFlush(new Receipt(cheque(nextNo(), "2000")));
		ReceiptMark mark = marks.saveAndFlush(new ReceiptMark(saved.getSerialNo(), ReceiptMark.Kind.CHEQUE_NOT_CLEARED,
				"Bounced", new BigDecimal("2000"), adminId, Instant.parse("2026-10-02T05:00:00Z")));

		assertThat(mark.getId()).isNotNull();
		ReceiptMark loaded = marks.findByReceiptSerialNo(saved.getSerialNo()).orElseThrow();
		assertThat(loaded.getAmountAddedBack()).isEqualTo(new BigDecimal("2000.00"));
		assertThat(loaded.getKind()).isEqualTo(ReceiptMark.Kind.CHEQUE_NOT_CLEARED);
		assertThat(marks.findByReceiptSerialNoIn(List.of(saved.getSerialNo(), -1L))).hasSize(1);
		assertThatThrownBy(() -> marks.saveAndFlush(new ReceiptMark(saved.getSerialNo(),
				ReceiptMark.Kind.CHEQUE_NOT_CLEARED, "Again", new BigDecimal("2000"), adminId, Instant.now())))
			.isInstanceOf(DataAccessException.class);
	}

	@Test
	void receiptsAreFoundByStudentNewestFirst() {
		Receipt first = receipts.saveAndFlush(new Receipt(cash(nextNo(), "0", "100")));
		Receipt second = receipts.saveAndFlush(new Receipt(cash(nextNo(), "0", "200")));

		List<Receipt> found = receipts.findByStudentIdOrderBySerialNoDesc(studentId);

		assertThat(found.stream().map(Receipt::getSerialNo).toList()).containsSubsequence(second.getSerialNo(),
				first.getSerialNo());
	}

	@Test
	void anAmountWithMoreThanTwoDecimalsIsRefusedNotRounded() {
		assertThatThrownBy(() -> new Receipt(cash(nextNo(), "0", "100.005"))).isInstanceOf(ArithmeticException.class);
	}

	@Test
	void theRepositoriesOfReceiptsHaveNoDeleteAndNoUpdate() {
		for (Class<?> type : new Class<?>[] { ReceiptRepository.class, ReceiptMarkRepository.class }) {
			assertThat(Arrays.stream(type.getMethods()).map(Method::getName))
				.noneMatch(name -> name.startsWith("delete") || name.startsWith("remove") || name.startsWith("update"));
		}
	}

	@Test
	void theSchoolNameComesFromTheTestProfileAndAddressAndPhoneAreEmpty() {
		assertThat(school.getName()).isEqualTo("Test School");
		assertThat(school.getAddress()).isEmpty();
		assertThat(school.getPhone()).isEmpty();
	}

	@EnableConfigurationProperties(SchoolProperties.class)
	static class SchoolOnly {

	}

	@Test
	void theMainProfileHasTheSchoolsRealNameAndABlankNameStopsTheStartup() {
		ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(SchoolOnly.class)
			.withInitializer(new ConfigDataApplicationContextInitializer());

		runner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(SchoolProperties.class).getName()).isEqualTo("Birlasagar Higher Secondary School");
		});
		runner.withPropertyValues("campusflow.school.name=").run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).hasStackTraceContaining("campusflow.school.name must be set");
		});
		runner.withPropertyValues("campusflow.school.name=   ").run(context -> assertThat(context).hasFailed());
	}

	@Test
	void aCreatedReceiptWritesOneEntryWithEveryAmountInFullAndChequeDetailsOnlyForCheque() {
		Receipt cashReceipt = receipts.saveAndFlush(new Receipt(cash(nextNo(), "500", "10000")));
		Receipt chequeReceipt = receipts.saveAndFlush(new Receipt(cheque(nextNo(), "2000")));

		tx.executeWithoutResult(status -> {
			feesAudit.receiptCreated(cashReceipt);
			feesAudit.receiptCreated(chequeReceipt);
		});

		Map<String, Object> cashEntry = entry(cashReceipt.getSerialNo());
		assertThat(cashEntry.get("action")).isEqualTo("Create");
		assertThat(cashEntry.get("area")).isEqualTo("Fees");
		String cashChanges = String.valueOf(cashEntry.get("changes"));
		assertThat(cashChanges).contains("\"registrationPaid\": \"500.00\"")
			.contains("\"feePaid\": \"10000.00\"")
			.contains("\"total\": \"10500.00\"")
			.contains("CASH")
			.doesNotContain("chequeNo")
			.doesNotContain("*");
		String chequeChanges = String.valueOf(entry(chequeReceipt.getSerialNo()).get("changes"));
		assertThat(chequeChanges).contains("\"chequeNo\": \"CQ1\"").contains("\"bankName\": \"Test Bank\"");
	}

	@Test
	void aChequeNotClearedEntryHasBothStatusesTheAmountThePaidUpToDatesAndTheReason() {
		Receipt saved = receipts.saveAndFlush(new Receipt(cheque(nextNo(), "2000")));

		tx.executeWithoutResult(status -> feesAudit.chequeNotCleared(saved.getSerialNo(), new BigDecimal("2000"),
				"2026-07-09", "2026-05-01", "Bounced"));

		Map<String, Object> entry = entry(saved.getSerialNo());
		assertThat(entry.get("action")).isEqualTo("Edit");
		assertThat(entry.get("reason")).isEqualTo("Bounced");
		assertThat(String.valueOf(entry.get("changes"))).contains("\"status\": \"VALID\"")
			.contains("\"status\": \"CHEQUE_NOT_CLEARED\"")
			.contains("\"amountAddedBack\": \"2000.00\"")
			.contains("\"paidUpToBefore\": \"2026-07-09\"")
			.contains("\"paidUpToAfter\": \"2026-05-01\"");
	}

	private Map<String, Object> entry(Long serialNo) {
		return jdbc.queryForMap("select * from audit_log where entity = ? order by id desc limit 1",
				"Receipt#" + serialNo);
	}

}
