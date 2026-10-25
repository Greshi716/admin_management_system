package com.admin.management.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import com.admin.management.shared.time.ClockConfig;

/** Test clock that can be set to a date or moved forward, for the current academic year and lock-out timing. */
public class MutableClock extends Clock {

	private volatile Instant instant = Instant.now();

	private final ZoneId zone;

	public MutableClock() {
		this(ClockConfig.SCHOOL_ZONE);
	}

	private MutableClock(ZoneId zone) {
		this.zone = zone;
	}

	@Override
	public ZoneId getZone() {
		return zone;
	}

	@Override
	public Clock withZone(ZoneId newZone) {
		MutableClock copy = new MutableClock(newZone);
		copy.instant = instant;
		return copy;
	}

	@Override
	public Instant instant() {
		return instant;
	}

	public void set(Instant newInstant) {
		this.instant = newInstant;
	}

	/** Sets the clock to noon on the given school-local date. */
	public void setDate(LocalDate date) {
		set(LocalDateTime.of(date, java.time.LocalTime.NOON).atZone(zone).toInstant());
	}

	public void advance(Duration duration) {
		set(instant.plus(duration));
	}

	/** Back to the real time. */
	public void reset() {
		set(Instant.now());
	}

}
