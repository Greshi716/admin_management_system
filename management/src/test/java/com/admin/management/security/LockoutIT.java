package com.admin.management.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignIn;
import com.admin.management.support.TestAdmin;

@Covers("FR-044")
class LockoutIT extends IntegrationTest {

	private static final String WRONG = "wrong-password-1";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	LoginAttemptService attempts;

	@BeforeEach
	void cleanState() {
		clock.reset();
		jdbc.update("update users set failed_attempts = 0, locked_until = null");
	}

	@Test
	void fiveFailuresPauseSignInForFiveMinutes() throws Exception {
		failTimes(3);
		SignIn.login(mvc, new MockHttpSession(), TestAdmin.USERNAME, WRONG).andExpect(status().isUnauthorized());

		SignIn.login(mvc, new MockHttpSession(), TestAdmin.USERNAME, WRONG)
			.andExpect(status().is(423))
			.andExpect(jsonPath("$.retryAfterSeconds").value(allOf(greaterThan(290), lessThanOrEqualTo(300))));
	}

	@Test
	void aCorrectPasswordIsRefusedDuringThePause() throws Exception {
		failTimes(5);

		SignIn.login(mvc, new MockHttpSession(), TestAdmin.USERNAME, TestAdmin.PASSWORD)
			.andExpect(status().is(423))
			.andExpect(jsonPath("$.retryAfterSeconds").exists());
	}

	@Test
	void signInWorksAgainAfterFiveMinutes() throws Exception {
		failTimes(5);

		clock.advance(Duration.ofMinutes(5).plusSeconds(1));

		SignIn.login(mvc, new MockHttpSession(), TestAdmin.USERNAME, TestAdmin.PASSWORD).andExpect(status().isOk());
	}

	@Test
	void thePauseIsAudited() throws Exception {
		int before = pauseRows();

		failTimes(5);

		assertThat(pauseRows()).isEqualTo(before + 1);
	}

	@Test
	void successResetsTheCounter() throws Exception {
		failTimes(4);
		SignIn.login(mvc, new MockHttpSession(), TestAdmin.USERNAME, TestAdmin.PASSWORD).andExpect(status().isOk());
		assertThat(jdbc.queryForObject("select failed_attempts from users", Integer.class)).isZero();

		failTimes(4);

		SignIn.login(mvc, new MockHttpSession(), TestAdmin.USERNAME, TestAdmin.PASSWORD).andExpect(status().isOk());
	}

	@Test
	void failuresAtTheSameMomentAreAllCountedAndNeverAnError() throws Exception {
		int threads = 4;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			CountDownLatch go = new CountDownLatch(1);
			List<Future<?>> done = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				done.add(pool.submit(() -> {
					go.await();
					attempts.recordFailure(TestAdmin.USERNAME);
					return null;
				}));
			}
			go.countDown();
			for (Future<?> future : done) {
				future.get(30, TimeUnit.SECONDS); // throws if any call failed, for example on a stale row version
			}
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(jdbc.queryForObject("select failed_attempts from users", Integer.class)).isEqualTo(threads);
	}

	private void failTimes(int times) throws Exception {
		for (int i = 0; i < times; i++) {
			SignIn.login(mvc, new MockHttpSession(), TestAdmin.USERNAME, WRONG);
		}
	}

	private int pauseRows() {
		return jdbc.queryForObject("""
				select count(*) from audit_log where area = 'Security' and changes->>'event' = 'paused'
				""", Integer.class);
	}

}
