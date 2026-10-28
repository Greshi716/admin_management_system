package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.FeeTestData.Setup;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignedInClient;

/** Which fee rows can exist in a Draft year (test plan FR-001 and FR-002 boundary and failure rows). */
@Covers("FR-001")
@Covers("FR-002")
class FeeRowRulesIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	private static String body(Long structureVersion, long standardId, Long categoryId, String amount,
			String registrationFee, String dueDate) {
		return "{\"structureVersion\":" + structureVersion + ",\"standardId\":" + standardId + ",\"categoryId\":"
				+ categoryId + ",\"amount\":" + amount + ",\"registrationFee\":" + registrationFee + ",\"dueDate\":"
				+ (dueDate == null ? "null" : "\"" + dueDate + "\"") + "}";
	}

	private ResultActions add(SignedInClient client, Setup setup, Long structureVersion, long standardId,
			Long categoryId, String amount) throws Exception {
		return client.post("/api/fee-structures/" + setup.yearId() + "/rows",
				body(structureVersion, standardId, categoryId, amount, "null", null));
	}

	private long rows(Setup setup) {
		return jdbc.queryForObject("select count(*) from fee_rows where academic_year_id = ?", Long.class, setup.yearId());
	}

	@Test
	void aCategoryRowAndABlankRowOfTheSameStandardLiveTogether() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "RuleBoth", LocalDate.of(3701, 4, 1), 1);
		SignedInClient client = SignedInClient.of(mvc);

		add(client, setup, null, setup.standard(0), null, "36000").andExpect(status().isCreated());
		add(client, setup, 1L, setup.standard(0), setup.category(0), "40000").andExpect(status().isCreated());

		assertThat(rows(setup)).isEqualTo(2);
	}

	@Test
	void anAmountOfZeroWithNoRegistrationFeeAndNoDueDateIsKeptAsEntered() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "RuleZero", LocalDate.of(3702, 4, 1), 1);

		SignedInClient.of(mvc).post("/api/fee-structures/" + setup.yearId() + "/rows",
				"{\"structureVersion\":null,\"standardId\":" + setup.standard(0) + ",\"amount\":0.00}")
			.andExpect(status().isCreated())
			.andExpect(content().string(containsString("\"amount\":0.00")))
			.andExpect(jsonPath("$.registrationFee").doesNotExist())
			.andExpect(jsonPath("$.dueDate").doesNotExist());

		assertThat(jdbc.queryForObject("select amount from fee_rows where academic_year_id = ?", BigDecimal.class,
				setup.yearId())).isEqualByComparingTo("0.00");
	}

	@Test
	void amountsAreStoredWithExactlyTwoDecimals() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "RuleScale", LocalDate.of(3703, 4, 1), 2);
		SignedInClient client = SignedInClient.of(mvc);

		add(client, setup, null, setup.standard(0), null, "28000.5").andExpect(status().isCreated());
		add(client, setup, 1L, setup.standard(1), null, "0.01").andExpect(status().isCreated());

		assertThat(jdbc.queryForObject("select amount::text from fee_rows where standard_id = ?", String.class,
				setup.standard(0))).isEqualTo("28000.50");
		assertThat(jdbc.queryForObject("select amount::text from fee_rows where standard_id = ?", String.class,
				setup.standard(1))).isEqualTo("0.01");
	}

	@Test
	void theSameStandardMayHaveARowInEveryYear() throws Exception {
		Setup first = FeeTestData.year(jdbc, "RuleYearA", LocalDate.of(3704, 4, 1), 1);
		Setup second = FeeTestData.year(jdbc, "RuleYearB", LocalDate.of(3705, 4, 1), 1);
		SignedInClient client = SignedInClient.of(mvc);

		add(client, first, null, first.standard(0), null, "30000").andExpect(status().isCreated());
		// Same standard id in another year: reuse the first year's standard so the uniqueness is shown to be per year.
		add(client, second, null, first.standard(0), null, "30000").andExpect(status().isCreated());

		assertThat(rows(first)).isEqualTo(1);
		assertThat(rows(second)).isEqualTo(1);
	}

	@Test
	void editingACategoryRowToAllCategoriesIsRefusedWhenABlankRowExists() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "RuleEditBlank", LocalDate.of(3706, 4, 1), 1);
		SignedInClient client = SignedInClient.of(mvc);
		add(client, setup, null, setup.standard(0), null, "36000").andExpect(status().isCreated());
		add(client, setup, 1L, setup.standard(0), setup.category(0), "40000").andExpect(status().isCreated());
		long categoryRow = jdbc.queryForObject("select id from fee_rows where category_id is not null and academic_year_id = ?",
				Long.class, setup.yearId());

		client.put("/api/fee-structures/" + setup.yearId() + "/rows/" + categoryRow,
				"{\"version\":0,\"structureVersion\":2,\"standardId\":" + setup.standard(0)
						+ ",\"categoryId\":null,\"amount\":40000}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.fieldErrors[0].field").value(anyOf(is("standardId"), is("categoryId"))));

		assertThat(jdbc.queryForObject("select category_id is not null from fee_rows where id = ?", Boolean.class,
				categoryRow)).isTrue();
	}

	@Test
	void aSecondBlankRowIsRefusedAndNothingIsSaved() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "RuleDupBlank", LocalDate.of(3707, 4, 1), 1);
		SignedInClient client = SignedInClient.of(mvc);
		add(client, setup, null, setup.standard(0), null, "30000").andExpect(status().isCreated());
		long audits = jdbc.queryForObject("select count(*) from audit_log where area = 'Fees'", Long.class);

		add(client, setup, 1L, setup.standard(0), null, "31000")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.fieldErrors[0].field").value(anyOf(is("standardId"), is("categoryId"))));

		assertThat(rows(setup)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from audit_log where area = 'Fees'", Long.class)).isEqualTo(audits);
	}

	@Test
	void aMissingAmountAndAMissingStandardAreEachRefusedBesideTheirField() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "RuleMissing", LocalDate.of(3708, 4, 1), 1);
		SignedInClient client = SignedInClient.of(mvc);

		client.post("/api/fee-structures/" + setup.yearId() + "/rows",
				"{\"structureVersion\":null,\"amount\":1000}")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[?(@.field=='standardId')]").exists());
		client.post("/api/fee-structures/" + setup.yearId() + "/rows",
				"{\"structureVersion\":null,\"standardId\":" + setup.standard(0) + "}")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[?(@.field=='amount')]").exists());

		assertThat(rows(setup)).isZero();
	}

	@Test
	void anUnknownYearIsNotFoundForTheRowEndpoint() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "RuleNoYear", LocalDate.of(3709, 4, 1), 1);
		SignedInClient client = SignedInClient.of(mvc);

		client.get("/api/fee-structures/999999999").andExpect(status().isNotFound());
		client.post("/api/fee-structures/999999999/rows", body(null, setup.standard(0), null, "1000", "null", null))
			.andExpect(status().isNotFound());
	}

	@Test
	void withoutASessionTheFeeStructureAnswers401AndShowsNoFeeData() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "RuleNoAuth", LocalDate.of(3710, 4, 1), 1);
		FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(0), null, "12345", null, null);

		mvc.perform(get("/api/fee-structures/" + setup.yearId()))
			.andExpect(status().isUnauthorized())
			.andExpect(content().string(org.hamcrest.Matchers.not(containsString("12345"))));
		mvc.perform(post("/api/fee-structures/" + setup.yearId() + "/rows").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(null, setup.standard(0), null, "1000", "null", null)))
			.andExpect(status().isUnauthorized());

		assertThat(rows(setup)).isEqualTo(1);
	}

	@Test
	void aStructureWithExactlyOneRowCanBeFinalised() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "RuleOneRow", LocalDate.of(3711, 4, 1), 1);
		SignedInClient client = SignedInClient.of(mvc);
		add(client, setup, null, setup.standard(0), null, "1000").andExpect(status().isCreated());

		client.post("/api/fee-structures/" + setup.yearId() + "/finalise", "{\"version\":1}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("Finalised"));
	}

	@Test
	void deletingTheOnlyDraftRowThenFinalisingIsRefused() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "RuleDelLast", LocalDate.of(3712, 4, 1), 1);
		SignedInClient client = SignedInClient.of(mvc);
		add(client, setup, null, setup.standard(0), null, "1000").andExpect(status().isCreated());
		long row = jdbc.queryForObject("select id from fee_rows where academic_year_id = ?", Long.class, setup.yearId());
		client.delete("/api/fee-structures/" + setup.yearId() + "/rows/" + row + "?version=0&structureVersion=1")
			.andExpect(status().isNoContent());
		long audits = jdbc.queryForObject("select count(*) from audit_log where action = 'Finalise'", Long.class);

		client.post("/api/fee-structures/" + setup.yearId() + "/finalise", "{\"version\":2}")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].message").value("Add at least one fee row before finalising."));

		assertThat(jdbc.queryForObject("select status from fee_year_status where academic_year_id = ?", String.class,
				setup.yearId())).isEqualTo("Draft");
		assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'Finalise'", Long.class))
			.isEqualTo(audits);
	}

}
