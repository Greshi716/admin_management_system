package com.admin.management.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** MockMvc calls made as the signed-in test admin, with the CSRF token and the session added. */
public final class SignedInClient {

	private final MockMvc mvc;

	private final MockHttpSession session;

	private SignedInClient(MockMvc mvc, MockHttpSession session) {
		this.mvc = mvc;
		this.session = session;
	}

	public static SignedInClient of(MockMvc mvc) throws Exception {
		return new SignedInClient(mvc, SignIn.asTestAdmin(mvc));
	}

	public ResultActions get(String url) throws Exception {
		return mvc.perform(get(url).session(session));
	}

	public ResultActions post(String url, String json) throws Exception {
		return mvc.perform(withBody(post(url), json));
	}

	public ResultActions put(String url, String json) throws Exception {
		return mvc.perform(withBody(put(url), json));
	}

	public ResultActions delete(String url) throws Exception {
		return mvc.perform(delete(url).with(csrf()).session(session));
	}

	private MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder builder, String json) {
		return builder.with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON).content(json);
	}

}
