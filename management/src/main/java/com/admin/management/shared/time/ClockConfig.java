package com.admin.management.shared.time;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The one clock the application reads "today" from. The school's local date (India time) decides the
 * current academic year (FR-012), so code never calls {@code LocalDate.now()} without this clock.
 */
@Configuration
public class ClockConfig {

	public static final ZoneId SCHOOL_ZONE = ZoneId.of("Asia/Kolkata");

	@Bean
	public Clock clock() {
		return Clock.system(SCHOOL_ZONE);
	}

}
