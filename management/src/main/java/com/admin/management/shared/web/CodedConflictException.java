package com.admin.management.shared.web;

/**
 * A conflict the screen must recognise by a code, such as {@code REVISE_CONFIRMATION_REQUIRED} or
 * {@code FEE_STRUCTURE_FINALISED}. Becomes HTTP 409 with the code and, when given, the message on the named field and
 * a {@code current} value the screen can show (for example the number of students a revise will recalculate).
 * {@link ConflictException} has neither a code nor a {@code current}, and {@link StaleVersionException} has a fixed
 * code, so neither fits.
 */
public class CodedConflictException extends RuntimeException {

	private final String code;

	private final String field;

	private final transient Object current;

	/**
	 * @param code what the screen switches on
	 * @param field the field to show the message beside, or null
	 * @param current extra data for the screen, serialised as {@code current}, or null
	 */
	public CodedConflictException(String code, String field, String message, Object current) {
		super(message);
		this.code = code;
		this.field = field;
		this.current = current;
	}

	public String getCode() {
		return code;
	}

	public String getField() {
		return field;
	}

	public Object getCurrent() {
		return current;
	}

}
