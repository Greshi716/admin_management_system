package com.admin.management.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;

/** F6: signup is off by default. */
@Covers("FR-001")
@Covers("FR-002")
class SignupDisabledIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Test
	void signupIsNotFoundAndConfigSaysOff() throws Exception {
		mvc.perform(get("/api/auth/config")).andExpect(status().isOk()).andExpect(jsonPath("$.signupEnabled").value(false));
		mvc.perform(post("/api/auth/signup").with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"X\",\"username\":\"nosignup\",\"password\":\"demo-pass-123\",\"confirmPassword\":\"demo-pass-123\"}"))
			.andExpect(status().isNotFound());
	}

}
