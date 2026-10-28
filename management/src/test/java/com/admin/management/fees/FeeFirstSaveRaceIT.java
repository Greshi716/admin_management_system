package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.FeeTestData.Setup;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignedInClient;

/**
 * Two windows saving the very first value at the same time: the second is told the value is stale, with the latest
 * value, and nothing is saved twice (FR-007, FR-013). The first window's insert is held uncommitted while the second
 * request runs, so the race is hit every time.
 */
@Covers("FR-007")
@Covers("FR-013")
class FeeFirstSaveRaceIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	BroughtForwardRepository broughtForward;

	@Autowired
	FeeYearStatusRepository statuses;

	@Autowired
	PlatformTransactionManager transactionManager;

	private final ExecutorService executor = Executors.newCachedThreadPool();

	@AfterAll
	void stopExecutor() {
		executor.shutdownNow();
	}

	@Test
	void theSecondFirstBroughtForwardSaveGetsStaleVersionWithTheValueTheOtherWindowSaved() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "RaceBf", LocalDate.of(3601, 4, 1), 1);
		long enrollment = FeeTestData.student(jdbc, setup, 360101, 0, 0);
		long student = jdbc.queryForObject("select student_id from student_enrollments where id = ?", Long.class,
				enrollment);
		SignedInClient client = SignedInClient.of(mvc);
		TransactionTemplate tx = new TransactionTemplate(transactionManager);

		Future<MockHttpServletResponse> second = tx.execute(status -> {
			assertThat(broughtForward.insertIfAbsent(student, new BigDecimal("1500.00"), "First window", Instant.now()))
				.isEqualTo(1);
			Future<MockHttpServletResponse> running = executor.submit(() -> client
				.put("/api/students/" + student + "/brought-forward",
						"{\"version\":null,\"amount\":2500,\"note\":\"Second window\"}")
				.andReturn()
				.getResponse());
			awaitBlockedSession();
			assertThat(running).isNotDone();
			return running;
		});

		MockHttpServletResponse response = second.get(30, TimeUnit.SECONDS);
		assertThat(response.getStatus()).isEqualTo(409);
		assertThat(response.getContentAsString()).contains("STALE_VERSION").contains("1500.00");
		assertThat(jdbc.queryForObject("select amount from fee_brought_forward where student_id = ?", BigDecimal.class,
				student)).isEqualByComparingTo("1500");
		assertThat(jdbc.queryForObject("select count(*) from audit_log where entity = ?", Integer.class,
				"BroughtForward#" + student)).isZero();
	}

	@Test
	void theSecondFirstFeeRowOfAYearGetsStaleVersionInsteadOfAGenericConflict() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "RaceRow", LocalDate.of(3602, 4, 1), 1);
		SignedInClient client = SignedInClient.of(mvc);
		TransactionTemplate tx = new TransactionTemplate(transactionManager);

		Future<MockHttpServletResponse> second = tx.execute(status -> {
			assertThat(statuses.insertIfAbsent(setup.yearId())).isEqualTo(1);
			Future<MockHttpServletResponse> running = executor.submit(() -> client
				.post("/api/fee-structures/" + setup.yearId() + "/rows",
						"{\"structureVersion\":null,\"standardId\":" + setup.standard(0)
								+ ",\"categoryId\":null,\"amount\":1000,\"registrationFee\":null,\"dueDate\":null}")
				.andReturn()
				.getResponse());
			awaitBlockedSession();
			assertThat(running).isNotDone();
			return running;
		});

		MockHttpServletResponse response = second.get(30, TimeUnit.SECONDS);
		assertThat(response.getStatus()).isEqualTo(409);
		assertThat(response.getContentAsString()).contains("STALE_VERSION");
		assertThat(jdbc.queryForObject("select count(*) from fee_rows where academic_year_id = ?", Integer.class,
				setup.yearId())).isZero();
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

}
