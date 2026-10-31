package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.FeeTestData.Fx;
import com.admin.management.support.FeeTestData.Setup;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.ReceiptApi;
import com.admin.management.support.SignedInClient;
import com.jayway.jsonpath.JsonPath;

/**
 * Two saves at the same moment never overpay and never share a receipt number. The race is made deterministic by
 * holding the advisory lock on a separate connection until both requests are waiting for it (no sleeps).
 */
@Covers("FR-005")
@Covers("FR-016")
class ReceiptConcurrencyIT extends IntegrationTest {

	static final String TODAY = "3671-10-01";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	DataSource dataSource;

	Setup setup;

	SignedInClient client;

	int students;

	@BeforeAll
	void seed() {
		setup = FeeTestData.year(jdbc, "ConcIT", LocalDate.of(3671, 4, 1), 1);
		FeeTestData.setStatus(jdbc, setup.yearId(), true);
	}

	@BeforeEach
	void today() throws Exception {
		clock.setDate(LocalDate.of(3671, 10, 1));
		client = SignedInClient.of(mvc);
	}

	Fx student() {
		return FeeTestData.studentWithAccount(jdbc, setup, 937000 + (++students), "1000.00", null);
	}

	private int waitingAdvisoryLocks() {
		return jdbc.queryForObject("select count(*) from pg_locks where locktype = 'advisory' and not granted",
				Integer.class);
	}

	private void awaitWaiters(int expected) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
		while (waitingAdvisoryLocks() < expected) {
			if (System.nanoTime() > deadline) {
				throw new AssertionError("Expected " + expected + " requests waiting for the lock");
			}
			Thread.onSpinWait();
		}
	}

	private Connection holdLock(String key) throws Exception {
		Connection holder = dataSource.getConnection();
		try (PreparedStatement ps = holder.prepareStatement("select pg_advisory_lock(hashtextextended(?, 0))")) {
			ps.setString(1, key);
			ps.execute();
		}
		return holder;
	}

	private void release(Connection holder, String key) throws Exception {
		try (PreparedStatement ps = holder.prepareStatement("select pg_advisory_unlock(hashtextextended(?, 0))")) {
			ps.setString(1, key);
			ps.execute();
		}
		finally {
			holder.close();
		}
	}

	private String saved(String json, int status) {
		String code = status == 201 ? "CREATED" : String.valueOf(JsonPath.<Object>read(json, "$.code"));
		return status + ":" + code;
	}

	@Test
	void twoSavesWithTheSameBalanceVersionLetExactlyOneWinAndNeverOverpay() throws Exception {
		Fx s = student();
		String token = ReceiptApi.token(client, s.studentId());
		String key = "student-fee:" + s.studentId();
		Connection holder = holdLock(key);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		List<Future<String>> results = new ArrayList<>();
		try {
			for (String no : new String[] { "C-1", "C-2" }) {
				results.add(pool.submit(() -> {
					var r = ReceiptApi.createWithToken(client, s.studentId(), token, ReceiptApi.cash(no, TODAY, "700.00"))
						.andReturn()
						.getResponse();
					return saved(r.getContentAsString(), r.getStatus());
				}));
			}
			awaitWaiters(2);
		}
		finally {
			release(holder, key);
		}
		List<String> outcomes = new ArrayList<>();
		for (Future<String> f : results) {
			outcomes.add(f.get(30, TimeUnit.SECONDS));
		}
		pool.shutdown();

		assertThat(outcomes).filteredOn(o -> o.startsWith("201")).hasSize(1);
		assertThat(outcomes).filteredOn(o -> o.startsWith("409")).hasSize(1);
		assertThat(outcomes).anyMatch(o -> o.equals("409:BALANCE_CHANGED") || o.equals("409:OVERPAYMENT"));
		assertThat(jdbc.queryForObject(
				"select coalesce(sum(fee_paid), 0) from receipts where student_id = ?", java.math.BigDecimal.class,
				s.studentId())).isEqualByComparingTo("700.00");
	}

	@Test
	void twoSavesWithFreshTokensForTheSameDueNeverTogetherOverpay() throws Exception {
		Fx s = student();
		String key = "student-fee:" + s.studentId();
		Connection holder = holdLock(key);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		List<Future<String>> results = new ArrayList<>();
		try {
			for (String no : new String[] { "C-3", "C-4" }) {
				results.add(pool.submit(() -> {
					var r = ReceiptApi.create(client, s.studentId(), ReceiptApi.cash(no, TODAY, "700.00"))
						.andReturn()
						.getResponse();
					return saved(r.getContentAsString(), r.getStatus());
				}));
			}
			awaitWaiters(2);
		}
		finally {
			release(holder, key);
		}
		List<String> outcomes = new ArrayList<>();
		for (Future<String> f : results) {
			outcomes.add(f.get(30, TimeUnit.SECONDS));
		}
		pool.shutdown();

		assertThat(outcomes).filteredOn(o -> o.startsWith("201")).hasSize(1);
		assertThat(jdbc.queryForObject("select coalesce(sum(fee_paid), 0) from receipts where student_id = ?",
				java.math.BigDecimal.class, s.studentId())).isLessThanOrEqualTo(new java.math.BigDecimal("1000.00"));
	}

	@Test
	void twoStudentsSavingTheSameReceiptNumberAtOnceLetExactlyOneWin() throws Exception {
		Fx a = student();
		Fx b = student();
		String key = "receipt-no:C-9";
		Connection holder = holdLock(key);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		List<Future<String>> results = new ArrayList<>();
		try {
			for (Fx s : new Fx[] { a, b }) {
				results.add(pool.submit(() -> {
					var r = ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("c-9", TODAY, "100.00"))
						.andReturn()
						.getResponse();
					String body = r.getContentAsString();
					String tag = r.getStatus() == 201 ? "CREATED" : (body.contains("receiptNo") ? "receiptNo" : "other");
					return r.getStatus() + ":" + tag;
				}));
			}
			awaitWaiters(2);
		}
		finally {
			release(holder, key);
		}
		List<String> outcomes = new ArrayList<>();
		for (Future<String> f : results) {
			outcomes.add(f.get(30, TimeUnit.SECONDS));
		}
		pool.shutdown();

		assertThat(outcomes).filteredOn(o -> o.startsWith("201")).hasSize(1);
		assertThat(outcomes).filteredOn(o -> o.startsWith("409")).hasSize(1);
		assertThat(outcomes).contains("409:receiptNo");
		assertThat(jdbc.queryForObject("select count(*) from receipts where upper(btrim(receipt_no)) = 'C-9'",
				Integer.class)).isEqualTo(1);
	}

}
