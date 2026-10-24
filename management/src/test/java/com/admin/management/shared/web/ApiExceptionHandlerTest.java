package com.admin.management.shared.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.admin.management.support.Covers;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@Covers("FR-040")
@Covers("FR-046")
@Covers("FR-011")
@Covers("FR-017")
@Covers("FR-020")
class ApiExceptionHandlerTest {

	MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(new Thrower()).setControllerAdvice(new ApiExceptionHandler()).build();
	}

	@Test
	void validationErrorsBecome422WithFieldErrors() throws Exception {
		mvc.perform(post("/t/validate").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
			.andExpect(jsonPath("$.fieldErrors[0].message").isNotEmpty());
	}

	@Test
	void staleVersionBecomes409WithCodeAndLatestRecord() throws Exception {
		mvc.perform(get("/t/stale"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_VERSION"))
			.andExpect(jsonPath("$.current.version").value(7))
			.andExpect(jsonPath("$.current.name").value("2026-27"));
	}

	@Test
	void optimisticLockFailureBecomes409StaleVersion() throws Exception {
		mvc.perform(get("/t/optimistic"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_VERSION"))
			.andExpect(jsonPath("$.current").doesNotExist());
	}

	@Test
	void overlappingYearsBecome409OnTheDateField() throws Exception {
		mvc.perform(get("/t/sql/23P01/ex_academic_years_no_overlap"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("These dates overlap another academic year."))
			.andExpect(jsonPath("$.fieldErrors[0].field").value("startDate"));
	}

	@Test
	void duplicateDivisionBecomes409() throws Exception {
		mvc.perform(get("/t/sql/23505/uq_divisions_year_standard_name"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
	}

	@Test
	void unknownUniqueConstraintStillBecomes409() throws Exception {
		mvc.perform(get("/t/sql/23505/uq_something_new"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("This value already exists."));
	}

	@Test
	void deleteBlockedByReferenceBecomes409InUse() throws Exception {
		mvc.perform(get("/t/fk-delete"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("in use")));
	}

	@Test
	void insertWithMissingReferenceBecomes409NotInUse() throws Exception {
		mvc.perform(get("/t/fk-insert"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("A referenced record does not exist."));
	}

	@Test
	void constraintNameIsReadFromPostgresMessage() {
		org.assertj.core.api.Assertions
			.assertThat(ApiExceptionHandler
				.constraintName("ERROR: duplicate key value violates unique constraint \"uq_x\"\n  Detail: ..."))
			.isEqualTo("uq_x");
		org.assertj.core.api.Assertions.assertThat(ApiExceptionHandler.constraintName("no name here")).isNull();
	}

	record Body(@NotBlank String name) {
	}

	@RestController
	static class Thrower {

		@PostMapping("/t/validate")
		void validate(@Valid @RequestBody Body body) {
		}

		@GetMapping("/t/stale")
		void stale() {
			throw new StaleVersionException(Map.of("name", "2026-27", "version", 7));
		}

		@GetMapping("/t/optimistic")
		void optimistic() {
			throw new ObjectOptimisticLockingFailureException("AcademicYear", 1L);
		}

		@GetMapping("/t/sql/{state}/{constraint}")
		void sql(@PathVariable String state, @PathVariable String constraint) {
			String reason = switch (state) {
				case "23P01" -> "conflicting key value violates exclusion constraint \"" + constraint + "\"";
				default -> "duplicate key value violates unique constraint \"" + constraint + "\"";
			};
			throw new DataIntegrityViolationException("could not execute statement",
					new SQLException("ERROR: " + reason, state));
		}

		@GetMapping("/t/fk-delete")
		void fkDelete() {
			throw new DataIntegrityViolationException("x", new SQLException(
					"ERROR: update or delete on table \"standards\" violates foreign key constraint \"fk_divisions_standard\" on table \"divisions\"",
					"23503"));
		}

		@GetMapping("/t/fk-insert")
		void fkInsert() {
			throw new DataIntegrityViolationException("x", new SQLException(
					"ERROR: insert or update on table \"divisions\" violates foreign key constraint \"fk_divisions_standard\"",
					"23503"));
		}

	}

}
