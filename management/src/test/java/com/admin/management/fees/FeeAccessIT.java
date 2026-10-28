package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.FeeTestData.Setup;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignIn;
import com.admin.management.support.SignedInClient;

/**
 * Who may call the fee endpoints and which endpoints do not exist: sign-in and CSRF on every path, and no way to set
 * or delete a per-student fee (test plan FR-005-F3, FR-009-F2, FR-010-F2, X-PERM-1, X-PERM-2, X-NODEL-1).
 */
@Covers("FR-005")
@Covers("FR-009")
@Covers("FR-010")
class FeeAccessIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	private record Fixture(Setup setup, long studentId, long rowId) {
	}

	private Fixture fixture(int yearNumber) throws Exception {
		Setup setup = FeeTestData.year(jdbc, "Access" + yearNumber, LocalDate.of(yearNumber, 4, 1), 1);
		long row = FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(0), null, "12345", null, null);
		FeeTestData.setStatus(jdbc, setup.yearId(), false);
		long enrollment = FeeTestData.student(jdbc, setup, yearNumber * 100 + 1, 0, 0);
		long student = jdbc.queryForObject("select student_id from student_enrollments where id = ?", Long.class, enrollment);
		return new Fixture(setup, student, row);
	}

	private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
		return builder.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	@Test
	void everyFeeEndpointAnswers401WithoutASession() throws Exception {
		Fixture f = fixture(3761);
		String structure = "/api/fee-structures/" + f.setup().yearId();
		String student = "/api/students/" + f.studentId();

		mvc.perform(get(structure)).andExpect(status().isUnauthorized())
			.andExpect(content().string(not(containsString("12345"))));
		mvc.perform(get(structure + "/choices")).andExpect(status().isUnauthorized());
		mvc.perform(json(post(structure + "/rows").with(csrf()), "{}")).andExpect(status().isUnauthorized());
		mvc.perform(json(put(structure + "/rows/" + f.rowId()).with(csrf()), "{}")).andExpect(status().isUnauthorized());
		mvc.perform(delete(structure + "/rows/" + f.rowId() + "?version=0&structureVersion=1").with(csrf()))
			.andExpect(status().isUnauthorized());
		mvc.perform(json(post(structure + "/finalise").with(csrf()), "{\"version\":1}"))
			.andExpect(status().isUnauthorized());
		mvc.perform(json(put(student + "/brought-forward").with(csrf()), "{\"amount\":1}"))
			.andExpect(status().isUnauthorized());
		mvc.perform(get(student + "/fees")).andExpect(status().isUnauthorized());
		mvc.perform(json(post("/api/students/search").with(csrf()), "{\"first\":0,\"rows\":5}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void aMutationWithoutTheCsrfTokenIsRefusedAndSavesNothing() throws Exception {
		Fixture f = fixture(3762);
		MockHttpSession session = SignIn.asTestAdmin(mvc);
		String structure = "/api/fee-structures/" + f.setup().yearId();

		mvc.perform(json(post(structure + "/rows").session(session),
				"{\"structureVersion\":null,\"standardId\":" + f.setup().standard(0) + ",\"categoryId\":"
						+ f.setup().category(0) + ",\"amount\":500}"))
			.andExpect(status().isForbidden());
		mvc.perform(json(post(structure + "/finalise").session(session), "{\"version\":0}"))
			.andExpect(status().isForbidden());
		mvc.perform(json(put("/api/students/" + f.studentId() + "/brought-forward").session(session),
				"{\"version\":null,\"amount\":1}"))
			.andExpect(status().isForbidden());

		assertThat(jdbc.queryForObject("select count(*) from fee_rows where academic_year_id = ?", Long.class,
				f.setup().yearId())).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from fee_year_status where academic_year_id = ? and status = 'Draft'",
				Long.class, f.setup().yearId())).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from fee_brought_forward where student_id = ?", Long.class,
				f.studentId())).isZero();
	}

	@Test
	void thereIsNoEndpointToSetOrDeleteAPerStudentFeeOrAccount() throws Exception {
		Fixture f = fixture(3763);
		SignedInClient client = SignedInClient.of(mvc);
		String fees = "/api/students/" + f.studentId() + "/fees";

		client.put(fees, "{\"amount\":1}").andExpect(status().is(anyOf(is(404), is(405))));
		client.post(fees, "{\"amount\":1}").andExpect(status().is(anyOf(is(404), is(405))));
		client.delete(fees).andExpect(status().is(anyOf(is(404), is(405))));
		client.delete("/api/students/" + f.studentId() + "/brought-forward")
			.andExpect(status().is(anyOf(is(404), is(405))));
		client.delete("/api/students/" + f.studentId()).andExpect(status().is(anyOf(is(404), is(405))));

		assertThat(jdbc.queryForObject("select count(*) from students where id = ?", Long.class, f.studentId()))
			.isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from student_enrollments where student_id = ?", Long.class,
				f.studentId())).isEqualTo(1);
	}

}
