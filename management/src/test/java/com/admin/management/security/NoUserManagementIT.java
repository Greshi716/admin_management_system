package com.admin.management.security;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignedInClient;

/** Test plan FR-003-F: there is no way to create users or roles through the API. */
@Covers("FR-003")
class NoUserManagementIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Test
	void usersAndRolesCannotBeCreatedThroughTheApi() throws Exception {
		SignedInClient api = SignedInClient.of(mvc);

		api.post("/api/users", "{\"username\":\"second\",\"password\":\"Abcdef12\"}").andExpect(status().is4xxClientError());
		api.post("/api/roles", "{\"name\":\"ADMIN\"}").andExpect(status().is4xxClientError());
	}

}
