package com.admin.management.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignedInClient;

@Covers("FR-018")
@Covers("FR-019")
@Covers("FR-020")
@Covers("FR-021")
@Covers("FR-046")
class StudentCategoryIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void backToTheSeed() {
		jdbc.update("delete from student_categories where code not in ('O/S','CEW','W','STAFF','S STAFF','RTE','CASUAL')");
		jdbc.update("update student_categories set active = true, affects_fees = false, name = code, version = 0");
	}

	@Test
	void theSevenSeededCategoriesAreListed() throws Exception {
		SignedInClient api = SignedInClient.of(mvc);

		api.post("/api/student-categories/search", "{\"rows\":100}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(7))
			.andExpect(jsonPath("$.rows[*].code")
				.value(containsInAnyOrder("O/S", "CEW", "W", "STAFF", "S STAFF", "RTE", "CASUAL")));
		api.get("/api/student-categories").andExpect(jsonPath("$.length()").value(7));
	}

	@Test
	void createNeedsCodeNameAndTheAffectsFeesChoice() throws Exception {
		SignedInClient api = SignedInClient.of(mvc);

		api.post("/api/student-categories", "{}")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[*].field").value(hasItems("code", "name", "affectsFees")));
		api.post("/api/student-categories", "{\"code\":\"NEW1\",\"name\":\"New one\"}")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("affectsFees"));
		api.post("/api/student-categories", "{\"code\":\"NEW1\",\"name\":\"New one\",\"affectsFees\":true}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.affectsFees").value(true))
			.andExpect(jsonPath("$.active").value(true));
	}

	@Test
	void aDuplicateCodeIsRefused() throws Exception {
		SignedInClient api = SignedInClient.of(mvc);

		api.post("/api/student-categories", "{\"code\":\"RTE\",\"name\":\"Again\",\"affectsFees\":false}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("code"));
	}

	@Test
	void editIsAuditedAndAStaleVersionIsRefused() throws Exception {
		SignedInClient api = SignedInClient.of(mvc);
		long id = jdbc.queryForObject("select id from student_categories where code = 'CASUAL'", Long.class);
		int before = jdbc.queryForObject("select count(*) from audit_log where entity = ?", Integer.class,
				"StudentCategory#" + id);

		api.put("/api/student-categories/" + id,
				"{\"code\":\"CASUAL\",\"name\":\"Casual\",\"affectsFees\":true,\"version\":0}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.affectsFees").value(true));
		api.put("/api/student-categories/" + id,
				"{\"code\":\"CASUAL\",\"name\":\"Other\",\"affectsFees\":false,\"version\":0}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_VERSION"))
			.andExpect(jsonPath("$.current.name").value("Casual"));

		assertThat(jdbc.queryForObject("select count(*) from audit_log where entity = ? and action = 'Edit' and area = 'Master data'",
				Integer.class, "StudentCategory#" + id)).isEqualTo(before + 1);
	}

	@Test
	void anUnreferencedCategoryCanBeDeletedAndItIsAudited() throws Exception {
		SignedInClient api = SignedInClient.of(mvc);
		api.post("/api/student-categories", "{\"code\":\"TEMP\",\"name\":\"Temp\",\"affectsFees\":false}")
			.andExpect(status().isCreated());
		long id = jdbc.queryForObject("select id from student_categories where code = 'TEMP'", Long.class);

		api.delete("/api/student-categories/" + id).andExpect(status().isNoContent());

		api.get("/api/student-categories").andExpect(jsonPath("$[*].code", org.hamcrest.Matchers.not(hasItem("TEMP"))));
		assertThat(jdbc.queryForObject("select count(*) from audit_log where entity = ? and action = 'Delete'",
				Integer.class, "StudentCategory#" + id)).isEqualTo(1);
	}

}
