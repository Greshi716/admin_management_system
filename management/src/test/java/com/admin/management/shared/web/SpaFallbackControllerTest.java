package com.admin.management.shared.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SpaFallbackControllerTest {

	MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(new SpaFallbackController()).build();
	}

	@Test
	void rootAndBrowserRoutesForwardToIndex() throws Exception {
		mvc.perform(get("/")).andExpect(forwardedUrl("/index.html"));
		mvc.perform(get("/login")).andExpect(forwardedUrl("/index.html"));
		mvc.perform(get("/master-data/academic-years")).andExpect(forwardedUrl("/index.html"));
		mvc.perform(get("/students/12/edit")).andExpect(forwardedUrl("/index.html"));
		mvc.perform(get("/a/b/c/d/e")).andExpect(forwardedUrl("/index.html"));
	}

	@Test
	void apiPathsAreNotForwarded() throws Exception {
		mvc.perform(get("/api")).andExpect(status().isNotFound());
		mvc.perform(get("/api/auth/me")).andExpect(status().isNotFound());
		mvc.perform(get("/api/academic-years/3")).andExpect(status().isNotFound());
	}

	@Test
	void pathsStartingWithApiButNotApiAreForwarded() throws Exception {
		mvc.perform(get("/apiary")).andExpect(forwardedUrl("/index.html"));
	}

	@Test
	void staticFilesWithAnExtensionAreNotForwarded() throws Exception {
		mvc.perform(get("/main-ABC123.js")).andExpect(status().isNotFound());
		mvc.perform(get("/assets/logo.png")).andExpect(status().isNotFound());
		mvc.perform(get("/media/font.woff2")).andExpect(status().isNotFound());
		mvc.perform(get("/assets/fonts/plus-jakarta.woff2")).andExpect(status().isNotFound());
		mvc.perform(get("/a/b/c/d/chunk-XYZ.js")).andExpect(status().isNotFound());
	}

}
