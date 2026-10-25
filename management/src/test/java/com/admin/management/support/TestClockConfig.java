package com.admin.management.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Adds a movable clock that wins over the application clock wherever a {@code Clock} is injected,
 * because it is a {@code Clock} itself and marked primary.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfig {

	@Bean
	@Primary
	public MutableClock testClock() {
		return new MutableClock();
	}

}
