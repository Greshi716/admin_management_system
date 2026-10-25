package com.admin.management.shared.web;

/**
 * Thrown by a service when the version sent by the client differs from the loaded record's version
 * (FR-046). Carries the latest record so the screen can show the current values.
 */
public class StaleVersionException extends RuntimeException {

	private final transient Object current;

	public StaleVersionException(Object current) {
		super("The record was changed by someone else after you opened it.");
		this.current = current;
	}

	public Object getCurrent() {
		return current;
	}

}
