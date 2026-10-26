package com.admin.management.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignedInClient;

/** Test plan FR-019-B/V: codes with spaces and slashes round-trip, and an edit that flips the flag bumps the version. */
@Covers("FR-019")
class StudentCategoryCodeIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@AfterEach
	void removeTestRows() {
		jdbc.update("delete from student_categories where code not in ('O/S','CEW','W','STAFF','S STAFF','RTE','CASUAL')");
	}

	@Test
	void aCodeWithASlashAndASpaceIsReturnedUnchanged() throws Exception {
		SignedInClient api = SignedInClient.of(mvc);

		api.post("/api/student-categories", "{\"code\":\"X/Y Z\",\"name\":\"Slash and space\",\"affectsFees\":false}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.code").value("X/Y Z"));
		long id = jdbc.queryForObject("select id from student_categories where code = 'X/Y Z'", Long.class);

		// there is no GET by id in the contract (specs/001-foundation); the list of active categories carries every field
		api.get("/api/student-categories")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == " + id + ")].code").value("X/Y Z"));
	}

	@Test
	void flippingTheFlagStoresItAndRaisesTheVersionByOne() throws Exception {
		SignedInClient api = SignedInClient.of(mvc);
		api.post("/api/student-categories", "{\"code\":\"FLIP\",\"name\":\"Flip\",\"affectsFees\":false}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.version").value(0));
		long id = jdbc.queryForObject("select id from student_categories where code = 'FLIP'", Long.class);

		api.put("/api/student-categories/" + id, "{\"code\":\"FLIP\",\"name\":\"Flip\",\"affectsFees\":true,\"version\":0}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version").value(1));

		api.get("/api/student-categories").andExpect(jsonPath("$[?(@.id == " + id + ")].affectsFees").value(true));
		assertThat(jdbc.queryForObject("select affects_fees from student_categories where id = ?", Boolean.class, id))
			.isTrue();
	}

}
