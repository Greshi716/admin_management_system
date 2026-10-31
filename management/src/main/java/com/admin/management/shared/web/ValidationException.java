package com.admin.management.shared.web;

import java.util.List;

/** Thrown for a request the server refuses on field-level grounds; becomes HTTP 422 with {@code fieldErrors}. */
public class ValidationException extends RuntimeException {

	private final transient List<ApiProblem.FieldError> fieldErrors;

	/** A machine-readable reason the screen can act on, or null for an ordinary field error. */
	private final String code;

	public ValidationException(List<ApiProblem.FieldError> fieldErrors) {
		this(null, fieldErrors);
	}

	public ValidationException(String code, List<ApiProblem.FieldError> fieldErrors) {
		super("Validation failed");
		this.code = code;
		this.fieldErrors = List.copyOf(fieldErrors);
	}

	public ValidationException(String field, String message) {
		this(null, List.of(new ApiProblem.FieldError(field, message)));
	}

	public List<ApiProblem.FieldError> getFieldErrors() {
		return fieldErrors;
	}

	public String getCode() {
		return code;
	}

}
