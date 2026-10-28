package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.admin.management.students.StudentInput;
import com.admin.management.students.StudentService;
import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.FeeTestData.Setup;
import com.admin.management.support.IntegrationTest;

/**
 * A class record added to a finalised year gets its fee account at once, and a registration and a finalise or revise
 * never overlap (FR-006, SC-002). The concurrency tests prove the order from the database's own lock wait state, not
 * from sleeps.
 */
@Covers("FR-006")
class FeeClassRecordIT extends IntegrationTest {

	@Autowired
	StudentService students;

	@Autowired
	FeeStructureService structures;

	@Autowired
	FeeYearStatusRepository statuses;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PlatformTransactionManager transactionManager;

	private final ExecutorService executor = Executors.newCachedThreadPool();

	@AfterAll
	void stopExecutor() {
		executor.shutdownNow();
	}

	/** A year that is the current one, with one standard, one all-categories row of 36,000.00 and 500.00 registration. */
	private Setup currentYear(String prefix, int yearNumber, boolean finalised) {
		Setup setup = FeeTestData.year(jdbc, prefix, LocalDate.of(yearNumber, 4, 1), 1);
		FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(0), null, "36000", "500", LocalDate.of(yearNumber, 6, 30));
		FeeTestData.setStatus(jdbc, setup.yearId(), finalised);
		clock.setDate(LocalDate.of(yearNumber, 10, 1));
		return setup;
	}

	private StudentInput input(Setup setup, String grNo) {
		return new StudentInput(null, grNo, "Hooksurname", "Alpha", "Middle", "Male", LocalDate.of(2012, 1, 1),
				"Test Town", "Test father", "Test mother", "9000000001", "9000000002", "Test address 1", "1234 5678 9012",
				"Test religion 77", setup.category(0), LocalDate.of(2020, 6, 1), setup.standard(0), "Test school",
				setup.standard(0), setup.divisionByStandard().get(setup.standard(0)));
	}

	private Long enrollmentOf(String grNo) {
		return jdbc.queryForObject("""
				select e.id from student_enrollments e join students s on s.id = e.student_id where s.gr_no = ?
				""", Long.class, grNo);
	}

	private long accountsOf(Setup setup) {
		return jdbc.queryForObject("select count(*) from fee_accounts where academic_year_id = ?", Long.class,
				setup.yearId());
	}

	/** Waits until another database session is waiting for a lock; fails after 30 seconds. */
	private void awaitBlockedSession() {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
		while (System.nanoTime() < deadline) {
			Integer waiting = jdbc.queryForObject("""
					select count(*) from pg_stat_activity
					where datname = current_database() and wait_event_type = 'Lock' and pid <> pg_backend_pid()
					""", Integer.class);
			if (waiting != null && waiting > 0) {
				return;
			}
			LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
		}
		fail("no database session started waiting for a lock");
	}

	@Test
	void aStudentRegisteredInAFinalisedYearHasAnAccountAtOnce() {
		Setup setup = currentYear("HookFin", 3301, true);

		students.register(input(setup, "HOOK-1"));

		BigDecimal amount = jdbc.queryForObject("select amount from fee_accounts where enrollment_id = ?",
				BigDecimal.class, enrollmentOf("HOOK-1"));
		assertThat(amount).isEqualByComparingTo("36000");
		assertThat(jdbc.queryForObject("select registration_fee from fee_accounts where enrollment_id = ?",
				BigDecimal.class, enrollmentOf("HOOK-1"))).isEqualByComparingTo("500");
	}

	@Test
	void aStudentRegisteredInADraftYearOrAYearWithNoStatusHasNoAccount() {
		Setup draft = currentYear("HookDraft", 3302, false);
		students.register(input(draft, "HOOK-2"));
		assertThat(accountsOf(draft)).isZero();

		Setup none = FeeTestData.year(jdbc, "HookNone", LocalDate.of(3303, 4, 1), 1);
		clock.setDate(LocalDate.of(3303, 10, 1));
		students.register(input(none, "HOOK-3"));
		assertThat(accountsOf(none)).isZero();
	}

	@Test
	void aRegistrationWaitsForARunningFinaliseOrReviseThenGetsItsAccount() throws Exception {
		Setup setup = currentYear("HookWait", 3304, true);
		TransactionTemplate tx = new TransactionTemplate(transactionManager);
		Future<?> registration = tx.execute(status -> {
			statuses.lockForUpdate(setup.yearId()).orElseThrow();
			Future<?> running = executor.submit(() -> students.register(input(setup, "HOOK-4")));
			awaitBlockedSession();
			assertThat(running).isNotDone();
			return running;
		});

		registration.get(30, TimeUnit.SECONDS);

		assertThat(jdbc.queryForObject("select amount from fee_accounts where enrollment_id = ?", BigDecimal.class,
				enrollmentOf("HOOK-4"))).isEqualByComparingTo("36000");
	}

	@Test
	void aFinaliseWaitsForARunningRegistrationThenIncludesTheNewRecord() throws Exception {
		Setup setup = currentYear("HookReverse", 3305, false);
		TransactionTemplate tx = new TransactionTemplate(transactionManager);
		Future<?> finalising = tx.execute(status -> {
			// The registration takes the share lock on the Draft year's status row and keeps it until this commits.
			students.register(input(setup, "HOOK-5"));
			Future<?> running = executor.submit(() -> structures.finalise(setup.yearId(), 0L));
			awaitBlockedSession();
			assertThat(running).isNotDone();
			return running;
		});

		finalising.get(30, TimeUnit.SECONDS);

		assertThat(jdbc.queryForObject("select amount from fee_accounts where enrollment_id = ?", BigDecimal.class,
				enrollmentOf("HOOK-5"))).isEqualByComparingTo("36000");
		assertThat(jdbc.queryForObject(
				"select changes::text from audit_log where action = 'Finalise' and entity = ?", String.class,
				"FeeStructure#" + setup.yearId())).contains("\"studentsRecalculated\": 1");
	}

}
