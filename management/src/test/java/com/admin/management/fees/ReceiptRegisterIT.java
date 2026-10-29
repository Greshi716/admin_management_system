package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.FeeTestData.Fx;
import com.admin.management.support.FeeTestData.Setup;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignedInClient;
import com.jayway.jsonpath.JsonPath;

/**
 * The receipt register (FR-019, quickstart 17): the columns, newest serial first, the date range, mode, standard and
 * status filters, the search by receipt number and student name, and the day-wise totals of the same filter. Every
 * receipt number starts with {@code RG-} and every request searches that prefix, so other tests' receipts stay out.
 */
@Covers("FR-019")
class ReceiptRegisterIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	Setup setup;

	SignedInClient client;

	long cash1; // 1 Oct, cash, 500.00 + 10,000.00

	long cash2; // 1 Oct, cash, 2,000.00

	long cheque1; // 2 Oct, cheque, 5,000.00, valid

	long notCleared; // 2 Oct, cheque, 3,000.00, marked

	long previous; // 3 Oct, cash, previous years 1,500.00

	@BeforeAll
	void seed() {
		setup = FeeTestData.year(jdbc, "RegIT", LocalDate.of(3961, 4, 1), 1);
		FeeTestData.setStatus(jdbc, setup.yearId(), true);
		Fx a = FeeTestData.studentWithAccount(jdbc, setup, 396101, "36500.00", "500.00");
		Fx b = FeeTestData.studentWithAccount(jdbc, setup, 396102, "36500.00", "500.00");
		cash1 = FeeTestData.registerReceipt(jdbc, setup, a, "RG-1", LocalDate.of(3961, 10, 1), "CASH", "CURRENT_YEAR",
				"500.00", "10000.00");
		cash2 = FeeTestData.registerReceipt(jdbc, setup, b, "RG-2", LocalDate.of(3961, 10, 1), "CASH", "CURRENT_YEAR",
				"0", "2000.00");
		cheque1 = FeeTestData.registerReceipt(jdbc, setup, a, "RG-3", LocalDate.of(3961, 10, 2), "CHEQUE",
				"CURRENT_YEAR", "0", "5000.00");
		notCleared = FeeTestData.registerReceipt(jdbc, setup, b, "RG-4", LocalDate.of(3961, 10, 2), "CHEQUE",
				"CURRENT_YEAR", "0", "3000.00");
		FeeTestData.markNotCleared(jdbc, notCleared, "Bounced");
		previous = FeeTestData.registerReceipt(jdbc, setup, a, "RG-5", LocalDate.of(3961, 10, 3), "CASH",
				"PREVIOUS_YEARS", "0", "1500.00");
	}

	@BeforeEach
	void signIn() throws Exception {
		client = SignedInClient.of(mvc);
	}

	private static String request(String filters) {
		return "{\"first\":0,\"rows\":25,\"globalFilter\":\"RG-\"" + (filters.isEmpty() ? "" : ",\"filters\":{" + filters + "}")
				+ "}";
	}

	private List<Map<String, Object>> rows(String filters) throws Exception {
		String body = client.post("/api/receipts/search", request(filters))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.rows");
	}

	private List<Object> receiptNos(String filters) throws Exception {
		return rows(filters).stream().map(r -> r.get("receiptNo")).toList();
	}

	private static String equalsFilter(String field, String value) {
		return "\"" + field + "\":{\"value\":\"" + value + "\",\"matchMode\":\"equals\"}";
	}

	private static BigDecimal money(Object value) {
		return new BigDecimal(String.valueOf(value));
	}

	@Test
	void theRegisterListsNewestSerialFirstWithTheRegisterColumns() throws Exception {
		List<Map<String, Object>> rows = rows("");

		assertThat(rows).extracting(r -> r.get("receiptNo")).containsExactly("RG-5", "RG-4", "RG-3", "RG-2", "RG-1");
		Map<String, Object> first = rows.get(4);
		assertThat(money(first.get("serialNo"))).isEqualByComparingTo(String.valueOf(cash1));
		assertThat(first.get("receiptDate")).isEqualTo("3961-10-01");
		assertThat(first.get("student")).isEqualTo("Register Student");
		assertThat(first.get("grNo")).isEqualTo("GR-396101");
		assertThat(first.get("stdDiv")).isEqualTo("Reg Std 1-A");
		assertThat(first.get("type")).isEqualTo("Current year");
		assertThat(first.get("mode")).isEqualTo("Cash");
		assertThat(money(first.get("registrationPaid"))).isEqualByComparingTo("500.00");
		assertThat(money(first.get("yearFee"))).isEqualByComparingTo("10000.00");
		assertThat(money(first.get("previousYearsFee"))).isEqualByComparingTo("0.00");
		assertThat(money(first.get("total"))).isEqualByComparingTo("10500.00");
		assertThat(first.get("status")).isEqualTo("Valid");
	}

	@Test
	void aPreviousYearsReceiptShowsItsAmountInThePreviousYearsColumnAndAChequeNotClearedShowsItsStatus()
			throws Exception {
		List<Map<String, Object>> rows = rows("");

		Map<String, Object> earlier = rows.get(0);
		assertThat(earlier.get("type")).isEqualTo("Previous years");
		assertThat(money(earlier.get("yearFee"))).isEqualByComparingTo("0.00");
		assertThat(money(earlier.get("previousYearsFee"))).isEqualByComparingTo("1500.00");

		Map<String, Object> marked = rows.get(1);
		assertThat(marked.get("mode")).isEqualTo("Cheque");
		assertThat(marked.get("status")).isEqualTo("Cheque not cleared");
	}

	@Test
	void theDateRangeIsInclusiveOnBothDays() throws Exception {
		assertThat(receiptNos("\"receiptDate\":{\"value\":[\"3961-10-02\",\"3961-10-02\"],\"matchMode\":\"dateRange\"}"))
			.containsExactly("RG-4", "RG-3");
		assertThat(receiptNos("\"receiptDate\":{\"value\":[\"3961-10-02\",\"3961-10-03\"],\"matchMode\":\"dateRange\"}"))
			.containsExactly("RG-5", "RG-4", "RG-3");
	}

	@Test
	void theModeStatusAndStandardFiltersKeepOnlyThoseReceipts() throws Exception {
		assertThat(receiptNos(equalsFilter("mode", "Cheque"))).containsExactly("RG-4", "RG-3");
		assertThat(receiptNos(equalsFilter("status", "Cheque not cleared"))).containsExactly("RG-4");
		assertThat(receiptNos(equalsFilter("status", "Valid"))).containsExactly("RG-5", "RG-3", "RG-2", "RG-1");
		assertThat(receiptNos("\"standardId\":{\"value\":" + setup.standard(0) + ",\"matchMode\":\"equals\"}"))
			.hasSize(5);
		assertThat(receiptNos("\"standardId\":{\"value\":" + (setup.standard(0) + 1_000_000) + ",\"matchMode\":\"equals\"}"))
			.isEmpty();
	}

	@Test
	void theSearchFindsAReceiptNumberAndAStudentName() throws Exception {
		String byNumber = client.post("/api/receipts/search",
				"{\"first\":0,\"rows\":25,\"globalFilter\":\"RG-3\"}").andReturn().getResponse().getContentAsString();
		assertThat((List<?>) JsonPath.read(byNumber, "$.rows[?(@.receiptNo=='RG-3')]")).hasSize(1);

		String byName = client.post("/api/receipts/search",
				"{\"first\":0,\"rows\":100,\"globalFilter\":\"Register Student\"}").andReturn().getResponse()
			.getContentAsString();
		assertThat((List<?>) JsonPath.read(byName, "$.rows[?(@.receiptNo=='RG-1')]")).hasSize(1);
	}

	@Test
	void theDayTotalsCountOnlyValidReceiptsPerDay() throws Exception {
		String body = client.post("/api/receipts/day-totals", request("")).andExpect(status().isOk()).andReturn()
			.getResponse().getContentAsString();

		assertThat((List<?>) JsonPath.read(body, "$")).hasSize(3);
		assertThat(JsonPath.<String>read(body, "$[0].date")).isEqualTo("3961-10-01");
		assertThat((Integer) JsonPath.read(body, "$[0].count")).isEqualTo(2);
		assertThat(money(JsonPath.read(body, "$[0].cash"))).isEqualByComparingTo("12500.00");
		assertThat(money(JsonPath.read(body, "$[0].cheque"))).isEqualByComparingTo("0.00");
		assertThat(money(JsonPath.read(body, "$[0].total"))).isEqualByComparingTo("12500.00");
		// 2 Oct: the cheque of 5,000.00 counts, the one marked Cheque not cleared does not.
		assertThat((Integer) JsonPath.read(body, "$[1].count")).isEqualTo(1);
		assertThat(money(JsonPath.read(body, "$[1].cheque"))).isEqualByComparingTo("5000.00");
		assertThat(money(JsonPath.read(body, "$[1].total"))).isEqualByComparingTo("5000.00");
		assertThat(money(JsonPath.read(body, "$[2].total"))).isEqualByComparingTo("1500.00");
	}

	@Test
	void theDayTotalsFollowTheFilter() throws Exception {
		String body = client.post("/api/receipts/day-totals", request(equalsFilter("mode", "Cheque")))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		assertThat((List<?>) JsonPath.read(body, "$")).hasSize(1);
		assertThat(money(JsonPath.read(body, "$[0].total"))).isEqualByComparingTo("5000.00");
	}

	@Test
	void theRegisterNeedsASignedInUser() throws Exception {
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/receipts/search")
			.contentType("application/json")
			.content(request("")))
			.andExpect(status().is4xxClientError());
	}

}
