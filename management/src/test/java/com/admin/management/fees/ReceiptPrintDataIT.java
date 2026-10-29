package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

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
import com.admin.management.support.ReceiptApi;
import com.admin.management.support.SignedInClient;

/**
 * What the print view needs is in the one receipt response (FR-017), and printing leaves no trace: two reads give the
 * same body, there is no print endpoint and no table or audit entry records a print (FR-018, owner answer Q8).
 */
@Covers("FR-017")
@Covers("FR-018")
class ReceiptPrintDataIT extends IntegrationTest {

	static final String TODAY = "3951-10-01";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	Setup setup;

	SignedInClient client;

	@BeforeAll
	void seed() {
		setup = FeeTestData.year(jdbc, "PrintIT", LocalDate.of(3951, 4, 1), 1);
		FeeTestData.setStatus(jdbc, setup.yearId(), true);
	}

	@BeforeEach
	void today() throws Exception {
		clock.setDate(LocalDate.of(3951, 10, 1));
		client = SignedInClient.of(mvc);
	}

	private long receipt(int n, String no) throws Exception {
		Fx s = FeeTestData.studentWithAccount(jdbc, setup, 944000 + n, "36500.00", "500.00");
		return ReceiptApi.serial(ReceiptApi.create(client, s.studentId(), "\"receiptNo\":\"" + no + "\",\"receiptDate\":\""
				+ TODAY + "\",\"registrationPaid\":500.00,\"feePaid\":10000.00,\"paymentMode\":\"CHEQUE\","
				+ "\"chequeNo\":\"778899\",\"bankName\":\"Test Bank\",\"chequeDate\":\"" + TODAY + "\""));
	}

	@Test
	void theReceiptResponseHoldsEveryPrintedFieldIncludingTheSchoolHeader() throws Exception {
		long serial = receipt(1, "PR-1");

		client.get("/api/receipts/" + serial).andExpect(status().isOk())
			.andExpect(jsonPath("$.receiptNo").value("PR-1"))
			.andExpect(jsonPath("$.receiptDate").value(TODAY))
			.andExpect(jsonPath("$.studentName").isNotEmpty())
			.andExpect(jsonPath("$.grNo").value("GR-944001"))
			.andExpect(jsonPath("$.standardName").isNotEmpty())
			.andExpect(jsonPath("$.divisionName").value("A"))
			.andExpect(jsonPath("$.academicYear").isNotEmpty())
			.andExpect(jsonPath("$.registrationPaid").value(500.00))
			.andExpect(jsonPath("$.feePaid").value(10000.00))
			.andExpect(jsonPath("$.total").value(10500.00))
			.andExpect(jsonPath("$.totalWords").value("Ten Thousand Five Hundred Rupees Only"))
			.andExpect(jsonPath("$.paymentMode").value("CHEQUE"))
			.andExpect(jsonPath("$.chequeNo").value("778899"))
			.andExpect(jsonPath("$.bankName").value("Test Bank"))
			.andExpect(jsonPath("$.chequeDate").value(TODAY))
			.andExpect(jsonPath("$.status").value("VALID"))
			.andExpect(jsonPath("$.school.name").isNotEmpty());
	}

	@Test
	void theResponseHasNoPaidUpToAndNoCopyMarkAndTwoReadsAreIdentical() throws Exception {
		long serial = receipt(2, "PR-2");

		String first = client.get("/api/receipts/" + serial).andReturn().getResponse().getContentAsString();
		String second = client.get("/api/receipts/" + serial).andReturn().getResponse().getContentAsString();

		assertThat(second).isEqualTo(first);
		assertThat(first.toLowerCase()).doesNotContain("paidupto").doesNotContain("duplicate").doesNotContain("copy")
			.doesNotContain("printcount").doesNotContain("printed");
	}

	@Test
	void thereIsNoPrintEndpointAndNoTableOrAuditEntryRecordsAPrint() throws Exception {
		long serial = receipt(3, "PR-3");
		long audit = jdbc.queryForObject("select count(*) from audit_log", Long.class);
		long marks = jdbc.queryForObject("select count(*) from receipt_marks", Long.class);

		client.get("/api/receipts/" + serial + "/print").andExpect(status().is4xxClientError());
		client.post("/api/receipts/" + serial + "/print", "{}").andExpect(status().is4xxClientError());
		client.get("/api/receipts/" + serial);
		client.get("/api/receipts/" + serial);

		assertThat(jdbc.queryForObject("select count(*) from audit_log", Long.class)).isEqualTo(audit);
		assertThat(jdbc.queryForObject("select count(*) from receipt_marks", Long.class)).isEqualTo(marks);
		List<String> tables = jdbc.queryForList("""
				select table_name from information_schema.tables
				where table_schema = current_schema() and (table_name ilike '%print%' or table_name ilike '%duplicate%')
				""", String.class);
		assertThat(tables).isEmpty();
		List<String> columns = jdbc.queryForList("""
				select column_name from information_schema.columns
				where table_schema = current_schema() and table_name in ('receipts', 'receipt_marks')
				  and (column_name ilike '%print%' or column_name ilike '%duplicate%' or column_name ilike '%copy%')
				""", String.class);
		assertThat(columns).isEmpty();
	}

}
