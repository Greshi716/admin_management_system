package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.FeeTestData.Fx;
import com.admin.management.support.FeeTestData.Setup;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.ReceiptApi;
import com.admin.management.support.SignIn;
import com.admin.management.support.SignedInClient;

/** The receipt endpoints need a signed-in session and a CSRF token, and their errors leak nothing (FR-021, FR-009). */
@Covers("FR-021")
@Covers("FR-009")
class ReceiptSecurityIT extends IntegrationTest {

	static final String TODAY = "3681-10-01";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	Setup setup;

	Fx student;

	SignedInClient client;

	@BeforeAll
	void seed() {
		setup = FeeTestData.year(jdbc, "SecIT", LocalDate.of(3681, 4, 1), 1);
		FeeTestData.setStatus(jdbc, setup.yearId(), true);
		student = FeeTestData.studentWithAccount(jdbc, setup, 938001, "1000.00", null);
	}

	@BeforeEach
	void today() throws Exception {
		clock.setDate(LocalDate.of(3681, 10, 1));
		client = SignedInClient.of(mvc);
	}

	@Test
	void everyReceiptEndpointIsRefusedWithoutASignedInSession() throws Exception {
		String base = "/api/students/" + student.studentId();
		mvc.perform(get(base + "/fee-collection")).andExpect(status().isUnauthorized());
		mvc.perform(post(base + "/fee-collection/preview").with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{}")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/receipts").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnauthorized());
		mvc.perform(get("/api/receipts/1")).andExpect(status().isUnauthorized());
	}

	@Test
	void savingWithoutACsrfTokenIsRefusedAndNothingIsSaved() throws Exception {
		MockHttpSession session = SignIn.asTestAdmin(mvc);
		String token = ReceiptApi.token(client, student.studentId());
		String body = "{\"studentId\":" + student.studentId() + ",\"receiptType\":\"CURRENT_YEAR\",\"balanceVersion\":\""
				+ token + "\"," + ReceiptApi.cash("S-1", TODAY, "10.00") + "}";

		mvc.perform(post("/api/receipts").session(session).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isForbidden());

		assertThat(jdbc.queryForObject("select count(*) from receipts where receipt_no = 'S-1'", Integer.class)).isZero();
	}

	@Test
	void refusalBodiesCarryNoStackTraceSqlOrClassNames() throws Exception {
		String conflict = ReceiptApi.create(client, student.studentId(), ReceiptApi.cash("S-2", TODAY, "99999.00"))
			.andExpect(status().isConflict())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String invalid = ReceiptApi.create(client, student.studentId(), ReceiptApi.cash("S-3", TODAY, "-1"))
			.andExpect(status().isUnprocessableEntity())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String stale = ReceiptApi.createWithToken(client, student.studentId(), "r0.m0.a0.b0",
				ReceiptApi.cash("S-4", TODAY, "10.00"))
			.andExpect(status().isConflict())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String missing = client.get("/api/receipts/999999999").andExpect(status().isNotFound()).andReturn()
			.getResponse()
			.getContentAsString();

		for (String body : new String[] { conflict, invalid, stale, missing }) {
			assertThat(body).doesNotContain("Exception", "at com.", "org.springframework", "select ", "insert ",
					"receipts_", "uq_", "SQL", "PSQL");
		}
	}

}
