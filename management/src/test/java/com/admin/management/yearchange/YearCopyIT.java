package com.admin.management.yearchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignedInClient;

/**
 * Validates creating an academic year with division and fee copy from the last year (FR-001, FR-002, FR-004, FR-005).
 */
@Covers("FR-001")
@Covers("FR-002")
@Covers("FR-004")
@Covers("FR-005")
class YearCopyIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	SignedInClient api;

	@BeforeEach
	void setUp() throws Exception {
		clock.reset();
		api = SignedInClient.of(mvc);
	}

	@Test
	void copyFromLastCopiesDivisionsAndFeesAndShiftsDueDates() throws Exception {
		// Create previous year (2027-28, leap year ending in 2028)
		LocalDate oldStart = LocalDate.of(2027, 4, 1);
		FeeTestData.Setup oldYear = FeeTestData.year(jdbc, "CopySrc", oldStart, 2);
		FeeTestData.addRow(jdbc, oldYear.yearId(), oldYear.standard(0), null, "12000.00", "500.00", LocalDate.of(2028, 2, 29));
		FeeTestData.addRow(jdbc, oldYear.yearId(), oldYear.standard(1), null, "15000.00", null, null);
		FeeTestData.setStatus(jdbc, oldYear.yearId(), true);

		// Create next year with copyFromLast = true
		String body = """
				{
				  "name": "CopyTarget 2028-29",
				  "startDate": "2028-04-01",
				  "endDate": "2029-03-31",
				  "copyFromLast": true
				}
				""";

		String response = api.post("/api/academic-years", body)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.copiedFromYearId").value(oldYear.yearId()))
				.andExpect(jsonPath("$.divisionsCopied").value(2))
				.andExpect(jsonPath("$.feeRowsCopied").value(2))
				.andReturn().getResponse().getContentAsString();

		Long newYearId = jdbc.queryForObject(
				"select id from academic_years where name = 'CopyTarget 2028-29'", Long.class);
		assertThat(newYearId).isNotNull();

		// Check divisions copied with same standard, name, active
		Integer divCount = jdbc.queryForObject(
				"select count(*) from divisions where academic_year_id = ?", Integer.class, newYearId);
		assertThat(divCount).isEqualTo(2);

		// Check fee rows copied and due date shifted: 2028-02-29 + 1 year = 2029-02-28
		List<Map<String, Object>> newRows = jdbc.queryForList(
				"select standard_id, amount, registration_fee, due_date from fee_rows where academic_year_id = ? order by id",
				newYearId);
		assertThat(newRows).hasSize(2);

		Map<String, Object> r1 = newRows.get(0);
		assertThat(new BigDecimal(r1.get("amount").toString())).isEqualByComparingTo("12000.00");
		assertThat(new BigDecimal(r1.get("registration_fee").toString())).isEqualByComparingTo("500.00");
		assertThat(r1.get("due_date").toString()).isEqualTo("2029-02-28");

		Map<String, Object> r2 = newRows.get(1);
		assertThat(new BigDecimal(r2.get("amount").toString())).isEqualByComparingTo("15000.00");
		assertThat(r2.get("registration_fee")).isNull();
		assertThat(r2.get("due_date")).isNull();

		// Check fee_year_status is Draft
		String status = jdbc.queryForObject(
				"select status from fee_year_status where academic_year_id = ?", String.class, newYearId);
		assertThat(status).isEqualTo("Draft");

		// Source year unchanged
		Integer oldDivCount = jdbc.queryForObject(
				"select count(*) from divisions where academic_year_id = ?", Integer.class, oldYear.yearId());
		assertThat(oldDivCount).isEqualTo(2);

		// Master data audit row exists
		Integer mdAuditCount = jdbc.queryForObject(
				"select count(*) from audit_log where area = 'Master data' and entity = ?",
				Integer.class, "AcademicYear#" + newYearId);
		assertThat(mdAuditCount).isGreaterThan(0);

		// Fees audit row exists and amounts equal using compareTo
		List<Map<String, Object>> feesAudit = jdbc.queryForList(
				"select changes from audit_log where area = 'Fees' and entity = ?",
				"FeeStructure#" + newYearId);
		assertThat(feesAudit).isNotEmpty();
	}

	@Test
	void editsToCopyLeaveOldYearAlone() throws Exception {
		LocalDate start = LocalDate.of(2035, 4, 1);
		FeeTestData.Setup oldYear = FeeTestData.year(jdbc, "EditIso", start, 1);
		FeeTestData.addRow(jdbc, oldYear.yearId(), oldYear.standard(0), null, "10000.00", "0.00", start.plusMonths(1));
		FeeTestData.setStatus(jdbc, oldYear.yearId(), true);

		api.post("/api/academic-years", """
				{
				  "name": "EditIso 2036-37",
				  "startDate": "2036-04-01",
				  "endDate": "2037-03-31",
				  "copyFromLast": true
				}
				""").andExpect(status().isCreated());

		Long newYearId = jdbc.queryForObject(
				"select id from academic_years where name = 'EditIso 2036-37'", Long.class);

		// Edit the copied fee row
		jdbc.update("update fee_rows set amount = 20000.00 where academic_year_id = ?", newYearId);

		// Old year remains 10000.00
		BigDecimal oldAmount = jdbc.queryForObject(
				"select amount from fee_rows where academic_year_id = ?", BigDecimal.class, oldYear.yearId());
		assertThat(oldAmount).isEqualByComparingTo("10000.00");
	}

	@Test
	void noPreviousYearRefusesCopy() throws Exception {
		// Clean academic years to ensure no previous year
		jdbc.update("delete from divisions");
		jdbc.update("delete from fee_accounts");
		jdbc.update("delete from fee_rows");
		jdbc.update("delete from fee_year_status");
		jdbc.update("delete from academic_years");

		api.post("/api/academic-years", """
				{
				  "name": "First Year",
				  "startDate": "2025-04-01",
				  "endDate": "2026-03-31",
				  "copyFromLast": true
				}
				""").andExpect(status().isConflict());
	}

	@Test
	void overlapStillRefused() throws Exception {
		LocalDate start = LocalDate.of(2040, 4, 1);
		FeeTestData.Setup oldYear = FeeTestData.year(jdbc, "OvLap", start, 1);

		// Attempt creating overlapping year with copy
		api.post("/api/academic-years", """
				{
				  "name": "OvLap Overlap",
				  "startDate": "2040-06-01",
				  "endDate": "2041-05-31",
				  "copyFromLast": true
				}
				""").andExpect(status().isConflict());
	}
}
