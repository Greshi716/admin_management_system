package com.admin.management.shared.web;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

/** Maps validation, constraint and version errors to the {@link ApiProblem} body (FR-040, FR-046). */
@RestControllerAdvice
public class ApiExceptionHandler {

	static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

	private static final String UNIQUE_VIOLATION = "23505";

	private static final String FOREIGN_KEY_VIOLATION = "23503";

	private static final String EXCLUSION_VIOLATION = "23P01";

	private static final Pattern CONSTRAINT_NAME = Pattern.compile("constraint \"([^\"]+)\"");

	/** Constraint name to the field it belongs to and the message shown next to it. */
	private static final Map<String, Known> KNOWN = Map.of(
			"uq_academic_years_name", new Known("name", "An academic year with this name already exists."),
			"ex_academic_years_no_overlap",
			new Known("startDate", "These dates overlap another academic year."),
			"uq_standards_name", new Known("name", "A standard with this name already exists."),
			"uq_divisions_year_standard_name",
			new Known("name", "This division already exists for the year and standard."),
			"uq_student_categories_code", new Known("code", "A category with this code already exists."),
			"uq_users_username", new Known("username", "This username is already taken."),
			"uq_students_gr_no_lower", new Known("grNo", "This GR No. is already used by a student."),
			"pk_student_gr_numbers", new Known("grNo", "This GR No. is already used by a student."));

	private record Known(String field, String message) {
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiProblem> invalidBody(MethodArgumentNotValidException ex) {
		List<ApiProblem.FieldError> errors = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(e -> new ApiProblem.FieldError(e.getField(), e.getDefaultMessage()))
			.toList();
		return validation(errors);
	}

	@ExceptionHandler(ValidationException.class)
	ResponseEntity<ApiProblem> invalidRequest(ValidationException ex) {
		return validation(ex.getFieldErrors());
	}

	@ExceptionHandler(NotFoundException.class)
	ResponseEntity<ApiProblem> notFound(NotFoundException ex) {
		return problem(ApiProblem.of(404, "Not found", ex.getMessage()));
	}

	@ExceptionHandler(ConflictException.class)
	ResponseEntity<ApiProblem> conflict(ConflictException ex) {
		return problem(ApiProblem.of(409, "Conflict", ex.getMessage())
			.withFieldErrors(List.of(new ApiProblem.FieldError(ex.getField(), ex.getMessage()))));
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ResponseEntity<ApiProblem> invalidEntity(ConstraintViolationException ex) {
		List<ApiProblem.FieldError> errors = ex.getConstraintViolations()
			.stream()
			.map(ApiExceptionHandler::toFieldError)
			.toList();
		return validation(errors);
	}

	@ExceptionHandler(StaleVersionException.class)
	ResponseEntity<ApiProblem> stale(StaleVersionException ex) {
		return problem(staleProblem().withCurrent(ex.getCurrent()));
	}

	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	ResponseEntity<ApiProblem> optimisticLock(ObjectOptimisticLockingFailureException ex) {
		return problem(staleProblem());
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<ApiProblem> integrity(DataIntegrityViolationException ex) {
		return fromCause(ex);
	}

	@ExceptionHandler(TransactionSystemException.class)
	ResponseEntity<ApiProblem> transaction(TransactionSystemException ex) {
		Throwable root = ex.getMostSpecificCause();
		if (root instanceof ConstraintViolationException violation) {
			return invalidEntity(violation);
		}
		return fromCause(ex);
	}

	private ResponseEntity<ApiProblem> fromCause(Exception ex) {
		Throwable cause = rootCause(ex);
		String sqlState = cause instanceof SQLException sql ? sql.getSQLState() : null;
		String message = cause.getMessage() == null ? "" : cause.getMessage();
		String constraint = constraintName(message);
		if (sqlState == null) {
			return problem(ApiProblem.of(409, "Conflict", "The change conflicts with existing data."));
		}
		return switch (sqlState) {
			case UNIQUE_VIOLATION -> duplicate(constraint, "This value already exists.");
			case EXCLUSION_VIOLATION -> duplicate(constraint, "These values overlap an existing record.");
			case FOREIGN_KEY_VIOLATION -> foreignKey(message);
			default -> problem(ApiProblem.of(409, "Conflict", "The change conflicts with existing data."));
		};
	}

	private ResponseEntity<ApiProblem> duplicate(String constraint, String fallback) {
		Known known = constraint == null ? null : KNOWN.get(constraint);
		if (known == null) {
			return problem(ApiProblem.of(409, "Conflict", fallback));
		}
		return problem(ApiProblem.of(409, "Conflict", known.message())
			.withFieldErrors(List.of(new ApiProblem.FieldError(known.field(), known.message()))));
	}

	/** A delete blocked by a reference means "in use"; an insert or update with a bad reference is a validation error. */
	private ResponseEntity<ApiProblem> foreignKey(String message) {
		if (message.contains("update or delete on table")) {
			return problem(ApiProblem.of(409, "Conflict",
					"This record is in use and cannot be deleted. Mark it inactive instead where that is allowed."));
		}
		return problem(ApiProblem.of(409, "Conflict", "A referenced record does not exist."));
	}

	private ResponseEntity<ApiProblem> validation(List<ApiProblem.FieldError> errors) {
		return problem(ApiProblem.of(422, "Validation failed", "Some fields need to be fixed.").withFieldErrors(errors));
	}

	private ApiProblem staleProblem() {
		return ApiProblem
			.of(409, "Conflict", "This record was changed by someone else after you opened it. The latest values are shown.")
			.withCode("STALE_VERSION");
	}

	private ResponseEntity<ApiProblem> problem(ApiProblem body) {
		return ResponseEntity.status(HttpStatus.valueOf(body.status())).contentType(PROBLEM_JSON).body(body);
	}

	private static ApiProblem.FieldError toFieldError(ConstraintViolation<?> violation) {
		return new ApiProblem.FieldError(violation.getPropertyPath().toString(), violation.getMessage());
	}

	static String constraintName(String message) {
		Matcher matcher = CONSTRAINT_NAME.matcher(message);
		return matcher.find() ? matcher.group(1) : null;
	}

	private static Throwable rootCause(Throwable ex) {
		Throwable cause = ex;
		while (cause.getCause() != null && cause.getCause() != cause) {
			cause = cause.getCause();
		}
		return cause;
	}

}
