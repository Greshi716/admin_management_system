package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.FeeTestData.Setup;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignedInClient;

/**
 * Money input on the three fee fields (fee amount, registration fee, brought-forward amount): negatives, too many
 * decimals and text are refused and never rounded; small values are kept exactly (test plan FR-011, X-MONEY-3).
 */
@Covers("FR-011")
class FeeMoneyInputIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	private String row(Long structureVersion, String amount, String registrationFee, long standardId) {
		return "{\"structureVersion\":" + structureVersion + ",\"standardId\":" + standardId + ",\"amount\":" + amount
				+ ",\"registrationFee\":" + registrationFee + "}";
	}

	private long rows(Setup setup) {
		return jdbc.queryForObject("select count(*) from fee_rows where academic_year_id = ?", Long.class, setup.yearId());
	}

	private long student(Setup setup, int n) {
		return jdbc.queryForObject("select student_id from student_enrollments where id = ?", Long.class,
				FeeTestData.student(jdbc, setup, n, 0, 0));
	}

	@Test
	void aNegativeAmountIsRefusedBesideEachOfTheThreeFields() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "MoneyNeg", LocalDate.of(3751, 4, 1), 1);
		long student = student(setup, 375101);
		SignedInClient client = SignedInClient.of(mvc);
		String url = "/api/fee-structures/" + setup.yearId() + "/rows";

		client.post(url, row(null, "-0.01", "null", setup.standard(0))).andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("amount"));
		client.post(url, row(null, "1000", "-0.01", setup.standard(0))).andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("registrationFee"));
		client.put("/api/students/" + student + "/brought-forward", "{\"version\":null,\"amount\":-0.01}")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("amount"));

		assertThat(rows(setup)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from fee_brought_forward where student_id = ?", Long.class,
				student)).isZero();
	}

	@Test
	void moreThanTwoDecimalsAreRefusedAndNeverRounded() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "MoneyDec", LocalDate.of(3752, 4, 1), 1);
		long student = student(setup, 375201);
		SignedInClient client = SignedInClient.of(mvc);
		String url = "/api/fee-structures/" + setup.yearId() + "/rows";

		for (String value : List.of("100.125", "100.124", "0.005")) {
			client.post(url, row(null, value, "null", setup.standard(0))).andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("amount"));
			client.post(url, row(null, "1000", value, setup.standard(0))).andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("registrationFee"));
			client.put("/api/students/" + student + "/brought-forward", "{\"version\":null,\"amount\":" + value + "}")
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("amount"));
		}

		assertThat(rows(setup)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from fee_brought_forward where student_id = ?", Long.class,
				student)).isZero();
	}

	@Test
	void textInsteadOfANumberIsRefusedWithAClientErrorNeverAServerError() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "MoneyText", LocalDate.of(3753, 4, 1), 1);
		SignedInClient client = SignedInClient.of(mvc);
		String url = "/api/fee-structures/" + setup.yearId() + "/rows";

		for (String value : List.of("\"abc\"", "\"1,000\"", "abc")) {
			client.post(url, row(null, value, "null", setup.standard(0))).andExpect(status().is4xxClientError());
		}

		assertThat(rows(setup)).isZero();
	}

	@Test
	void theSmallestAmountIsKeptAndBinaryFractionsGetNoFloatNoise() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "MoneySmall", LocalDate.of(3754, 4, 1), 2);
		SignedInClient client = SignedInClient.of(mvc);
		String url = "/api/fee-structures/" + setup.yearId() + "/rows";

		client.post(url, row(null, "0.01", "null", setup.standard(0))).andExpect(status().isCreated());
		client.post(url, row(1L, "0.1", "0.07", setup.standard(1)))
			.andExpect(status().isCreated())
			.andExpect(content().string(containsString("\"amount\":0.10")))
			.andExpect(content().string(containsString("\"registrationFee\":0.07")))
			.andExpect(content().string(not(containsString("0.07000000000000001"))));

		assertThat(jdbc.queryForObject("select amount::text from fee_rows where standard_id = ?", String.class,
				setup.standard(0))).isEqualTo("0.01");
		assertThat(jdbc.queryForObject("select amount::text from fee_rows where standard_id = ?", String.class,
				setup.standard(1))).isEqualTo("0.10");
		assertThat(jdbc.queryForObject("select registration_fee::text from fee_rows where standard_id = ?", String.class,
				setup.standard(1))).isEqualTo("0.07");
	}

	@Test
	void theSmallestBroughtForwardAmountIsKept() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "MoneyBf", LocalDate.of(3755, 4, 1), 1);
		long student = student(setup, 375501);

		SignedInClient.of(mvc).put("/api/students/" + student + "/brought-forward", "{\"version\":null,\"amount\":0.01}")
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("\"amount\":0.01")));

		assertThat(jdbc.queryForObject("select amount::text from fee_brought_forward where student_id = ?", String.class,
				student)).isEqualTo("0.01");
	}

}
