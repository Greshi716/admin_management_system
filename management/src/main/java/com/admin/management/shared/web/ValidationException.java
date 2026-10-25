package com.admin.management.shared.web;

import java.util.List;

/** Thrown for a request the server refuses on field-level grounds; becomes HTTP 422 with {@code fieldErrors}. */
public class ValidationException extends RuntimeException {

	private final transient List<ApiProblem.FieldError> fieldErrors;

	public ValidationException(List<ApiProblem.FieldError> fieldErrors) {
		super("Validation failed");
		this.fieldErrors = List.copyOf(fieldErrors);
	}

	public ValidationException(String field, String message) {
		this(List.of(new ApiProblem.FieldError(field, message)));
	}

	public List<ApiProblem.FieldError> getFieldErrors() {
		return fieldErrors;
	}

}
