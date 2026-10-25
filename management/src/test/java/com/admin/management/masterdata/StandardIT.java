package com.admin.management.masterdata;

import static org.hamcrest.Matchers.contains;
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

@Covers("FR-014")
@Covers("FR-015")
class StandardIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	SignedInClient api;

	@BeforeEach
	void signInAndEmpty() throws Exception {
		jdbc.update("delete from divisions");
		jdbc.update("delete from standards");
		api = SignedInClient.of(mvc);
	}

	@Test
	void createWithNameOrderAndNoStream() throws Exception {
		api.post("/api/standards", "{\"name\":\"Standard 1\",\"displayOrder\":1}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("Standard 1"))
			.andExpect(jsonPath("$.displayOrder").value(1))
			.andExpect(jsonPath("$.stream").isEmpty())
			.andExpect(jsonPath("$.active").value(true));
	}

	@Test
	void nameAndDisplayOrderAreRequired() throws Exception {
		api.post("/api/standards", "{}")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[*].field").value(hasItems("name", "displayOrder")));
	}

	@Test
	void aNameLongerThan100CharactersIsRefusedBesideTheField() throws Exception {
		api.post("/api/standards", "{\"name\":\"" + "x".repeat(101) + "\",\"displayOrder\":1}")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
	}

	@Test
	void streamMayOnlyBeScienceOrCommerce()throws Exception {
		api.post("/api/standards", "{\"name\":\"Standard 11 Sci\",\"displayOrder\":11,\"stream\":\"Science\"}")
			.andExpect(status().isCreated());
		api.post("/api/standards", "{\"name\":\"Standard 11 Com\",\"displayOrder\":12,\"stream\":\"Commerce\"}")
			.andExpect(status().isCreated());
		api.post("/api/standards", "{\"name\":\"Standard 11 Arts\",\"displayOrder\":13,\"stream\":\"Arts\"}")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("stream"));
	}

	@Test
	void theNameMustBeUnique() throws Exception {
		api.post("/api/standards", "{\"name\":\"Standard 1\",\"displayOrder\":1}").andExpect(status().isCreated());

		api.post("/api/standards", "{\"name\":\"Standard 1\",\"displayOrder\":2}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
	}

	@Test
	void listsAreInDisplayOrderByDefault() throws Exception {
		api.post("/api/standards", "{\"name\":\"Standard 3\",\"displayOrder\":3}").andExpect(status().isCreated());
		api.post("/api/standards", "{\"name\":\"Standard 1\",\"displayOrder\":1}").andExpect(status().isCreated());
		api.post("/api/standards", "{\"name\":\"Standard 2\",\"displayOrder\":2}").andExpect(status().isCreated());

		api.get("/api/standards").andExpect(jsonPath("$[*].name").value(contains("Standard 1", "Standard 2", "Standard 3")));
		api.post("/api/standards/search", "{}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(3))
			.andExpect(jsonPath("$.rows[*].name").value(contains("Standard 1", "Standard 2", "Standard 3")));
	}

	@Test
	void editChangesTheStandard() throws Exception {
		api.post("/api/standards", "{\"name\":\"Standard 1\",\"displayOrder\":1}").andExpect(status().isCreated());
		long id = jdbc.queryForObject("select id from standards", Long.class);

		api.put("/api/standards/" + id, "{\"name\":\"Std 1\",\"displayOrder\":5,\"stream\":\"Science\",\"version\":0}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Std 1"))
			.andExpect(jsonPath("$.displayOrder").value(5))
			.andExpect(jsonPath("$.stream").value("Science"))
			.andExpect(jsonPath("$.version").value(1));
	}

}
