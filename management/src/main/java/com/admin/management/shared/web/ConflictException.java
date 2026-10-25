package com.admin.management.shared.web;

/**
 * Thrown when a change clashes with existing data in a way the admin can fix, such as dates that overlap another
 * academic year; becomes HTTP 409 with the message on the named field.
 */
public class ConflictException extends RuntimeException {

	private final String field;

	public ConflictException(String field, String message) {
		super(message);
		this.field = field;
	}

	public String getField() {
		return field;
	}

}
