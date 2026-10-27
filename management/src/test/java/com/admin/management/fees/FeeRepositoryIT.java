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
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.StudentTestData;
import com.admin.management.support.StudentTestData.YearSetup;

/** The mapping of the fee tables, the no-delete rule, the locks and the audit writer (constitution IV, V). */
@Covers("FR-001")
@Covers("FR-006")
@Covers("FR-007")
@Covers("FR-011")
@Covers("FR-012")
class FeeRepositoryIT extends IntegrationTest {

	@Autowired
	FeeRowRepository rows;

	@Autowired
	FeeYearStatusRepository statuses;

	@Autowired
	FeeAccountRepository accounts;

	@Autowired
	BroughtForwardRepository broughtForward;

	@Autowired
	FeesAudit feesAudit;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PlatformTransactionManager transactionManager;

	TransactionTemplate tx;

	int years;

	long standardId;

	long categoryId;

	long studentId;

	long enrollmentId;

	long enrolledYearId;

	@BeforeAll
	void seed() {
		tx = new TransactionTemplate(transactionManager);
		standardId = jdbc.queryForObject("insert into standards (name, display_order) values ('Repo Std', 1) returning id",
				Long.class);
		categoryId = jdbc.queryForObject("select min(id) from student_categories", Long.class);
		enrolledYearId = freshYear();
		long division = jdbc.queryForObject(
				"insert into divisions (academic_year_id, standard_id, name) values (?, ?, 'A') returning id", Long.class,
				enrolledYearId, standardId);
		studentId = StudentTestData.insertStudent(jdbc, StudentTestData.valid(1, categoryId, standardId));
		StudentTestData.insertEnrollment(jdbc, studentId,
				new YearSetup(enrolledYearId, LocalDate.of(3000, 4, 1), standardId, division), categoryId);
		enrollmentId = jdbc.queryForObject("select id from student_enrollments where student_id = ?", Long.class,
				studentId);
	}

	@Test
	void aFeeRowRoundTripsWithTwoDecimals() {
		long year = freshYear();
		FeeRow saved = rows.saveAndFlush(new FeeRow(year, standardId, categoryId, new BigDecimal("28000"),
				new BigDecimal("500.5"), LocalDate.of(3000, 6, 30)));

		assertThat(jdbc.queryForObject("select amount::text from fee_rows where id = ?", String.class, saved.getId()))
			.isEqualTo("28000.00");
		assertThat(jdbc.queryForObject("select registration_fee::text from fee_rows where id = ?", String.class,
				saved.getId())).isEqualTo("500.50");
		FeeRow loaded = rows.findById(saved.getId()).orElseThrow();
		assertThat(loaded.getAmount()).isEqualTo(new BigDecimal("28000.00"));
		assertThat(loaded.getAmount().scale()).isEqualTo(2);
		assertThat(loaded.getRegistrationFee()).isEqualTo(new BigDecimal("500.50"));
		assertThat(loaded.getDueDate()).isEqualTo(LocalDate.of(3000, 6, 30));
		assertThat(loaded.getVersion()).isZero();
	}

	@Test
	void anAmountWithMoreThanTwoDecimalsIsNeverRoundedByTheEntity() {
		assertThatThrownBy(() -> new FeeRow(1L, standardId, null, new BigDecimal("100.123"), null, null))
			.isInstanceOf(ArithmeticException.class);
		assertThat(new FeeRow(1L, standardId, null, new BigDecimal("100.100"), null, null).getAmount())
			.isEqualTo(new BigDecimal("100.10"));
	}

	@Test
	void rowsAreFoundByYearAndByStandardAndCategoryWithOrWithoutACategory() {
		long year = freshYear();
		rows.saveAndFlush(new FeeRow(year, standardId, null, new BigDecimal("100"), null, null));
		rows.saveAndFlush(new FeeRow(year, standardId, categoryId, new BigDecimal("200"), null, null));

		assertThat(rows.findByAcademicYearIdOrderById(year)).extracting(FeeRow::getAmount)
			.containsExactly(new BigDecimal("100.00"), new BigDecimal("200.00"));
		assertThat(rows.findByAcademicYearIdAndStandardIdAndCategoryId(year, standardId, null)).get()
			.extracting(FeeRow::getAmount)
			.isEqualTo(new BigDecimal("100.00"));
		assertThat(rows.findByAcademicYearIdAndStandardIdAndCategoryId(year, standardId, categoryId)).get()
			.extracting(FeeRow::getAmount)
			.isEqualTo(new BigDecimal("200.00"));
		assertThat(rows.countByAcademicYearId(year)).isEqualTo(2);
	}

	@Test
	void aStaleVersionCannotOverwriteARow() {
		long year = freshYear();
		FeeRow saved = rows.saveAndFlush(
				new FeeRow(year, standardId, null, new BigDecimal("100"), null, null));
		FeeRow stale = rows.findById(saved.getId()).orElseThrow();
		tx.executeWithoutResult(status -> {
			FeeRow fresh = rows.findById(saved.getId()).orElseThrow();
			fresh.change(standardId, null, new BigDecimal("150"), null, null);
			rows.saveAndFlush(fresh);
		});

		stale.change(standardId, null, new BigDecimal("999"), null, null);
		assertThatThrownBy(() -> rows.saveAndFlush(stale)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
		assertThat(rows.findById(saved.getId()).orElseThrow().getAmount()).isEqualTo(new BigDecimal("150.00"));
	}

	@Test
	void theStatusStartsAsDraftAndTheLocksTakeTheRow() {
		long year = freshYear();
		statuses.saveAndFlush(new FeeYearStatus(year));

		tx.executeWithoutResult(status -> {
			FeeYearStatus write = statuses.lockForUpdate(year).orElseThrow();
			assertThat(write.getStatus()).isEqualTo(FeeYearStatus.DRAFT);
			assertThat(write.isFinalised()).isFalse();
			assertThat(write.getFinalisedAt()).isNull();
		});
		Optional<FeeYearStatus> shared = tx.execute(status -> statuses.lockForShare(year));
		Optional<FeeYearStatus> missing = tx.execute(status -> statuses.lockForUpdate(-1L));
		assertThat(shared).isPresent();
		assertThat(missing).isEmpty();
		// the lock needs a transaction, so a caller cannot forget it
		assertThatThrownBy(() -> statuses.lockForUpdate(year)).isInstanceOf(IllegalTransactionStateException.class);
	}

	@Test
	void lockingAndBumpingRaisesTheVersionByOneAndFinalisingKeepsTheTime() {
		long year = freshYear();
		statuses.saveAndFlush(new FeeYearStatus(year));
		Long before = statuses.findById(year).orElseThrow().getVersion();

		tx.executeWithoutResult(status -> statuses.lockForUpdateAndBump(year).orElseThrow());
		assertThat(statuses.findById(year).orElseThrow().getVersion()).isEqualTo(before + 1);

		Instant now = Instant.parse("3000-07-01T10:00:00Z");
		tx.executeWithoutResult(status -> {
			FeeYearStatus locked = statuses.lockForUpdate(year).orElseThrow();
			locked.finalise(now);
			statuses.saveAndFlush(locked);
		});
		FeeYearStatus finalised = statuses.findById(year).orElseThrow();
		assertThat(finalised.isFinalised()).isTrue();
		assertThat(finalised.getFinalisedAt()).isEqualTo(now);
		assertThat(finalised.getVersion()).isEqualTo(before + 2);
	}

	@Test
	void aFeeAccountWithAnAssignedIdIsInsertedAndKeepsNullsAndScale() {
		accounts.saveAndFlush(new FeeAccount(enrollmentId, enrolledYearId, null, null, null, Instant.now()));
		FeeAccount notSet = accounts.findById(enrollmentId).orElseThrow();
		assertThat(notSet.getAmount()).isNull();
		assertThat(notSet.getRegistrationFee()).isNull();

		tx.executeWithoutResult(status -> {
			FeeAccount account = accounts.findById(enrollmentId).orElseThrow();
			account.change(new BigDecimal("36000"), new BigDecimal("500"), LocalDate.of(3000, 6, 30), Instant.now());
			accounts.saveAndFlush(account);
		});
		FeeAccount set = accounts.findById(enrollmentId).orElseThrow();
		assertThat(set.getAmount()).isEqualTo(new BigDecimal("36000.00"));
		assertThat(set.getRegistrationFee()).isEqualTo(new BigDecimal("500.00"));
		assertThat(accounts.countByAcademicYearId(enrolledYearId)).isEqualTo(1);
		assertThat(accounts.findByAcademicYearId(enrolledYearId)).hasSize(1);
	}

	@Test
	void broughtForwardKeepsAmountAndNoteAndAStaleVersionIsRefused() {
		long student = StudentTestData.insertStudent(jdbc, StudentTestData.valid(2, categoryId, standardId));
		broughtForward.saveAndFlush(new BroughtForward(student, new BigDecimal("2500"), "old dues", Instant.now()));
		BroughtForward stale = broughtForward.findById(student).orElseThrow();
		assertThat(stale.getAmount()).isEqualTo(new BigDecimal("2500.00"));
		assertThat(stale.getNote()).isEqualTo("old dues");

		tx.executeWithoutResult(status -> {
			BroughtForward fresh = broughtForward.findById(student).orElseThrow();
			fresh.change(new BigDecimal("1000.50"), null, Instant.now());
			broughtForward.saveAndFlush(fresh);
		});
		stale.change(new BigDecimal("1"), "x", Instant.now());
		assertThatThrownBy(() -> broughtForward.saveAndFlush(stale))
			.isInstanceOf(ObjectOptimisticLockingFailureException.class);
		assertThat(broughtForward.findById(student).orElseThrow().getAmount()).isEqualTo(new BigDecimal("1000.50"));
	}

	@Test
	void accountsAndBroughtForwardCannotBeDeletedThroughTheirRepositories() {
		for (Class<?> repository : List.of(FeeAccountRepository.class, BroughtForwardRepository.class,
				FeeYearStatusRepository.class)) {
			assertThat(Arrays.stream(repository.getMethods()).map(Method::getName))
				.as(repository.getSimpleName() + " has no delete method")
				.noneMatch(name -> name.startsWith("delete") || name.startsWith("remove"));
		}
	}

	@Test
	void aRowChangeWritesOneEntryWithOnlyTheChangedFieldsAndAmountsInFull() {
		long year = freshYear();
		FeeRow row = rows.saveAndFlush(new FeeRow(year, standardId, null, new BigDecimal("36000"),
				new BigDecimal("500"), LocalDate.of(3000, 6, 30)));
		var before = row.snapshot();
		tx.executeWithoutResult(status -> feesAudit.rowCreated(row.getId(), before, null, null));
		row.change(standardId, null, new BigDecimal("37000"), new BigDecimal("500"), LocalDate.of(3000, 6, 30));
		var after = row.snapshot();

		tx.executeWithoutResult(status -> feesAudit.rowEdited(row.getId(), before, after, null, null));
		tx.executeWithoutResult(status -> feesAudit.rowEdited(row.getId(), after, after, null, null)); // nothing changed

		List<Map<String, Object>> entries = entriesOf("FeeRow#" + row.getId());
		assertThat(entries).extracting(e -> e.get("action")).containsExactly("Create", "Edit");
		String edit = String.valueOf(entries.get(1).get("changes"));
		assertThat(edit).contains("36000.00").contains("37000.00").doesNotContain("500.00").doesNotContain("dueDate");
		assertThat(String.valueOf(entries.get(0).get("changes"))).contains("\"amount\": \"36000.00\"");
	}

	@Test
	void aReviseEntryHasTheReasonAndBothCountsAndFinaliseHasItsOwnAction() {
		long year = freshYear();
		FeeRow row = rows.saveAndFlush(new FeeRow(year, standardId, null, new BigDecimal("100"), null, null));
		var before = row.snapshot();
		row.change(standardId, null, new BigDecimal("200"), null, null);
		var after = row.snapshot();

		tx.executeWithoutResult(status -> {
			feesAudit.rowEdited(row.getId(), before, after, FeesAudit.REVISE, new FeesAudit.Recalculated(5, 2));
			feesAudit.rowDeleted(row.getId(), after, FeesAudit.REVISE, new FeesAudit.Recalculated(5, 5));
			feesAudit.finalised(year, 5, 5, 3);
		});

		List<Map<String, Object>> entries = entriesOf("FeeRow#" + row.getId());
		assertThat(entries).extracting(e -> e.get("action")).containsExactly("Edit", "Delete");
		assertThat(entries).extracting(e -> e.get("reason")).containsExactly("Revise", "Revise");
		assertThat(String.valueOf(entries.get(0).get("changes"))).contains("\"studentsRecalculated\": 5")
			.contains("\"accountsChanged\": 2");
		List<Map<String, Object>> finalise = entriesOf("FeeStructure#" + year);
		assertThat(finalise).extracting(e -> e.get("action")).containsExactly("Finalise");
		assertThat(String.valueOf(finalise.get(0).get("changes"))).contains("\"studentsRecalculated\": 5")
			.contains("\"accountsChanged\": 5")
			.contains("\"rows\": 3");
	}

	@Test
	void aBroughtForwardEntryHoldsBothAmountsInFullAndNeverTheNoteText() {
		long student = StudentTestData.insertStudent(jdbc, StudentTestData.valid(3, categoryId, standardId));
		tx.executeWithoutResult(status -> {
			feesAudit.broughtForwardSaved(student, null, new BigDecimal("2500.00"), true);
			feesAudit.broughtForwardSaved(student, new BigDecimal("2500.00"), new BigDecimal("1000.50"), false);
			feesAudit.broughtForwardSaved(student, new BigDecimal("1000.50"), new BigDecimal("1000.50"), true);
			feesAudit.broughtForwardSaved(student, new BigDecimal("1000.5"), new BigDecimal("1000.50"), false); // nothing
		});

		List<Map<String, Object>> entries = entriesOf("BroughtForward#" + student);
		assertThat(entries).extracting(e -> e.get("action")).containsExactly("Create", "Edit", "Edit");
		assertThat(String.valueOf(entries.get(0).get("changes"))).contains("2500.00").contains("\"noteChanged\": true");
		assertThat(String.valueOf(entries.get(1).get("changes"))).contains("2500.00")
			.contains("1000.50")
			.doesNotContain("noteChanged");
		assertThat(String.valueOf(entries.get(2).get("changes"))).contains("\"noteChanged\": true").doesNotContain("amount");
	}

	private List<Map<String, Object>> entriesOf(String entity) {
		return jdbc.queryForList("select action, reason, changes::text as changes from audit_log where entity = ? order by id",
				entity);
	}

	/** A year no other test has used; 3000-01, 3001-02 and so on never overlap. */
	private long freshYear() {
		int from = 3000 + years++;
		return jdbc.queryForObject(
				"insert into academic_years (name, start_date, end_date) values (?, ?::date, ?::date) returning id",
				Long.class, from + "-" + (from + 1), from + "-04-01", (from + 1) + "-03-31");
	}

}
