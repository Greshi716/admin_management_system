package com.admin.management.shared.web;

/** Thrown when a record asked for by id does not exist (any more); becomes HTTP 404. */
public class NotFoundException extends RuntimeException {

	public NotFoundException(String what, Long id) {
		super(what + " " + id + " was not found.");
	}

}
