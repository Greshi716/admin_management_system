package com.admin.management.shared.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignedInClient;

/**
 * The packaged app: with the Angular build copied into the static folder, a browser route that is refreshed gets
 * {@code index.html} (the test classpath holds a stand-in page), and an unknown API path still answers 404 and
 * never the page. The whole application is running here, so the security rules apply too.
 */
@Covers("FR-037")
class SpaFallbackIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Test
	void browserRouteIsForwardedToIndexWithoutSigningIn() throws Exception {
		mvc.perform(get("/master-data/standards")).andExpect(forwardedUrl("/index.html"));
		mvc.perform(get("/audit-log")).andExpect(forwardedUrl("/index.html"));
	}

	@Test
	void indexPageIsServedFromTheStaticFolder() throws Exception {
		mvc.perform(get("/index.html"))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("<app-root>")));
	}

	@Test
	void unknownApiPathReturnsNotFoundWhenSignedIn() throws Exception {
		SignedInClient.of(mvc).get("/api/unknown").andExpect(status().isNotFound());
	}

	@Test
	void unknownApiPathReturnsJsonNotThePageWhenSignedOut() throws Exception {
		mvc.perform(get("/api/unknown"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("unauthenticated"));
	}

}
