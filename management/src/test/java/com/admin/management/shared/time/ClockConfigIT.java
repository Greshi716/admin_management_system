package com.admin.management.shared.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.MutableClock;

@Covers("FR-012")
class ClockConfigIT extends IntegrationTest {

	@Autowired
	Clock injected;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Test
	void testsGetTheMovableClockInTheSchoolZone() {
		assertThat(injected).isInstanceOf(MutableClock.class);
		assertThat(injected.getZone()).isEqualTo(ClockConfig.SCHOOL_ZONE);
	}

	@Test
	void clockCanBeSetToADateAndMovedForward() {
		clock.setDate(LocalDate.of(2027, 3, 31));
		assertThat(LocalDate.now(injected)).isEqualTo(LocalDate.of(2027, 3, 31));

		clock.advance(Duration.ofDays(1));
		assertThat(LocalDate.now(injected)).isEqualTo(LocalDate.of(2027, 4, 1));
		clock.reset();
	}

	@Test
	void applicationClockUsesIndiaTime() {
		assertThat(new ClockConfig().clock().getZone().getId()).isEqualTo("Asia/Kolkata");
	}

	@Test
	void passwordsAreHashedWithBcrypt() {
		String hash = passwordEncoder.encode("Some-Test-Password-1");
		assertThat(hash).startsWith("$2").isNotEqualTo("Some-Test-Password-1");
		assertThat(passwordEncoder.matches("Some-Test-Password-1", hash)).isTrue();
	}

}
