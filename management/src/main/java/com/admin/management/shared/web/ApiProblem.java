package com.admin.management.shared.web;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Error body for every failed API call, matching {@code Problem} in contracts/openapi.yaml. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiProblem(String title, int status, String detail, String code, Integer retryAfterSeconds,
		List<FieldError> fieldErrors, Object current) {

	public record FieldError(String field, String message) {
	}

	public static ApiProblem of(int status, String title, String detail) {
		return new ApiProblem(title, status, detail, null, null, null, null);
	}

	public ApiProblem withCode(String newCode) {
		return new ApiProblem(title, status, detail, newCode, retryAfterSeconds, fieldErrors, current);
	}

	public ApiProblem withFieldErrors(List<FieldError> errors) {
		return new ApiProblem(title, status, detail, code, retryAfterSeconds, errors, current);
	}

	public ApiProblem withCurrent(Object latest) {
		return new ApiProblem(title, status, detail, code, retryAfterSeconds, fieldErrors, latest);
	}

	public ApiProblem withRetryAfterSeconds(int seconds) {
		return new ApiProblem(title, status, detail, code, seconds, fieldErrors, current);
	}

}
