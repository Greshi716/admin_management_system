package com.admin.management.security;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

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

/** Test plan FR-044-B2: one second before the 5-minute pause ends the correct password is still refused. */
@Covers("FR-044")
class LockoutExpiryBoundaryIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void cleanState() {
		clock.reset();
		jdbc.update("update users set failed_attempts = 0, locked_until = null");
	}

	@Test
	void fourMinutesFiftyNineSecondsAfterThe5thFailureIsStillPaused() throws Exception {
		for (int i = 0; i < 5; i++) {
			SignIn.login(mvc, new MockHttpSession(), TestAdmin.USERNAME, "wrong-password-1");
		}

		clock.advance(Duration.ofMinutes(4).plusSeconds(59));

		SignIn.login(mvc, new MockHttpSession(), TestAdmin.USERNAME, TestAdmin.PASSWORD).andExpect(status().is(423));
	}

	@Test
	void fourFailuresDoNotPauseSignIn() throws Exception {
		for (int i = 0; i < 4; i++) {
			SignIn.login(mvc, new MockHttpSession(), TestAdmin.USERNAME, "wrong-password-1")
				.andExpect(status().isUnauthorized());
		}

		SignIn.login(mvc, new MockHttpSession(), TestAdmin.USERNAME, TestAdmin.PASSWORD).andExpect(status().isOk());
	}

}
