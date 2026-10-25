package com.admin.management.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Sign-in calls for MockMvc tests. */
public final class SignIn {

	private SignIn() {
	}

	/** Posts the login body; the caller checks the status. */
	public static ResultActions login(MockMvc mvc, MockHttpSession session, String username, String password)
			throws Exception {
		return mvc.perform(post("/api/auth/login").with(csrf())
			.session(session)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)));
	}

	/** Signs in as the known test admin and returns the session that carries the sign-in. */
	public static MockHttpSession asTestAdmin(MockMvc mvc) throws Exception {
		MockHttpSession session = new MockHttpSession();
		login(mvc, session, TestAdmin.USERNAME, TestAdmin.PASSWORD).andExpect(status().isOk());
		return session;
	}

}
