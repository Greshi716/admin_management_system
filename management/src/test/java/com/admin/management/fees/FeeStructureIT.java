package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.FeeTestData.Setup;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignedInClient;

/** Adding, editing and deleting fee rows of a Draft year, the refusals, the structure version and the audit entries. */
@Covers("FR-001")
@Covers("FR-002")
@Covers("FR-003")
@Covers("FR-011")
@Covers("FR-012")
@Covers("FR-013")
class FeeStructureIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	private static String body(Long structureVersion, Long version, long standardId, Long categoryId, String amount,
			String registrationFee, String dueDate) {
		return "{" + (version == null ? "" : "\"version\":" + version + ",") + "\"structureVersion\":" + structureVersion
				+ ",\"standardId\":" + standardId + ",\"categoryId\":" + categoryId + ",\"amount\":" + amount
				+ ",\"registrationFee\":" + registrationFee + ",\"dueDate\":"
				+ (dueDate == null ? "null" : "\"" + dueDate + "\"") + "}";
	}

	private ResultActions add(SignedInClient client, Setup setup, Long structureVersion, long standardId,
			Long categoryId, String amount) throws Exception {
		return client.post("/api/fee-structures/" + setup.yearId() + "/rows",
				body(structureVersion, null, standardId, categoryId, amount, "null", null));
	}

	private long count(String sql, Object... args) {
		return jdbc.queryForObject(sql, Long.class, args);
	}

	private long onlyRowOf(Setup setup) {
		return jdbc.queryForObject("select id from fee_rows where academic_year_id = ?", Long.class, setup.yearId());
	}

	@Test
	void addingTheFirstRowCreatesTheStatusRowAndAnAuditEntry() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "RowAdd", LocalDate.of(3101, 4, 1), 1);
		SignedInClient client = SignedInClient.of(mvc);

		client.post("/api/fee-structures/" + setup.yearId() + "/rows",
				body(null, null, setup.standard(0), null, "28000", "500", "3101-06-30"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.standardName").value("RowAdd Std 1"))
			.andExpect(jsonPath("$.categoryName").value("All categories"))
			.andExpect(jsonPath("$.version").value(0))
			.andExpect(content().string(containsString("\"amount\":28000.00")))
			.andExpect(content().string(containsString("\"registrationFee\":500.00")));

		client.get("/api/fee-structures/" + setup.yearId())
			.andExpect(jsonPath("$.status").value("Draft"))
			.andExpect(jsonPath("$.version").value(1))
			.andExpect(jsonPath("$.rows[0].dueDate").value("3101-06-30"));
		assertThat(count("select count(*) from audit_log where area = 'Fees' and action = 'Create' and entity = ?",
				"FeeRow#" + onlyRowOf(setup))).isEqualTo(1);
		assertThat(jdbc.queryForObject("select changes::text from audit_log where entity = ?", String.class,
				"FeeRow#" + onlyRowOf(setup))).contains("28000.00").contains("500.00");
	}

	@Test
	void editingInADraftYearSavesAtOnceAndBumpsTheStructureVersion() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "RowEdit", LocalDate.of(3102, 4, 1), 1);
		SignedInClient client = SignedInClient.of(mvc);
		add(client, setup, null, setup.standard(0), null, "28000").andExpect(status().isCreated());
		long rowId = onlyRowOf(setup);

		client.put("/api/fee-structures/" + setup.yearId() + "/rows/" + rowId,
				body(1L, 0L, setup.standard(0), null, "30000.5", "null", null))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version").value(1))
			.andExpect(content().string(containsString("\"amount\":30000.50")));

		client.get("/api/fee-structures/" + setup.yearId()).andExpect(jsonPath("$.version").value(2));
		assertThat(count("select count(*) from audit_log where area = 'Fees' and action = 'Edit' and entity = ?",
				"FeeRow#" + rowId)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select changes::text from audit_log where entity = ? and action = 'Edit'",
				String.class, "FeeRow#" + rowId)).contains("30000.50").contains("28000.00");
	}

	@Test
	void deletingInADraftYearRemovesTheRowAndKeepsWhatItHeldInTheAuditLog() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "RowDelete", LocalDate.of(3103, 4, 1), 1);
		SignedInClient client = SignedInClient.of(mvc);
		add(client, setup, null, setup.standard(0), null, "12000").andExpect(status().isCreated());
		long rowId = onlyRowOf(setup);

		client.delete("/api/fee-structures/" + setup.yearId() + "/rows/" + rowId + "?version=0&structureVersion=1")
			.andExpect(status().isNoContent());

		assertThat(count("select count(*) from fee_rows where academic_year_id = ?", setup.yearId())).isZero();
		assertThat(jdbc.queryForObject("select changes::text from audit_log where entity = ? and action = 'Delete'",
				String.class, "FeeRow#" + rowId)).contains("12000.00");
		client.get("/api/fee-structures/" + setup.yearId()).andExpect(jsonPath("$.version").value(2));
	}

	@Test
	void aSecondRowForTheSameStandardAndCategoryIsRefusedBesideTheField() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "RowDup", LocalDate.of(3104, 4, 1), 1);
		SignedInClient client = SignedInClient.of(mvc);
		add(client, setup, null, setup.standard(0), null, "1000").andExpect(status().isCreated());
		add(client, setup, 1L, setup.standard(0), setup.category(0), "2000").andExpect(status().isCreated());

		add(client, setup, 2L, setup.standard(0), null, "3000")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("standardId"));
		add(client, setup, 2L, setup.standard(0), setup.category(0), "3000")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("categoryId"));

		assertThat(count("select count(*) from fee_rows where academic_year_id = ?", setup.yearId())).isEqualTo(2);
	}

	@Test
	void editingARowIntoAnotherRowsPlaceIsRefused() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "RowDupEdit", LocalDate.of(3105, 4, 1), 2);
		SignedInClient client = SignedInClient.of(mvc);
		add(client, setup, null, setup.standard(0), null, "1000").andExpect(status().isCreated());
		add(client, setup, 1L, setup.standard(1), null, "2000").andExpect(status().isCreated());
		long second = jdbc.queryForObject("select id from fee_rows where standard_id = ?", Long.class,
				setup.standard(1));

		client.put("/api/fee-structures/" + setup.yearId() + "/rows/" + second,
				body(2L, 0L, setup.standard(0), null, "2000", "null", null))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("standardId"));
	}

	@Test
	void aNegativeAThreeDecimalAndAMissingAmountAreRefusedNotRounded() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "RowAmount", LocalDate.of(3106, 4, 1), 1);
		SignedInClient client = SignedInClient.of(mvc);

		add(client, setup, null, setup.standard(0), null, "-1")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("amount"));
		add(client, setup, null, setup.standard(0), null, "100.123")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("amount"));
		add(client, setup, null, setup.standard(0), null, "null")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("amount"));
		client.post("/api/fee-structures/" + setup.yearId() + "/rows",
				body(null, null, setup.standard(0), null, "1000", "-5", null))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("registrationFee"));
		client.post("/api/fee-structures/" + setup.yearId() + "/rows",
				body(null, null, setup.standard(0), null, "1000", "5.005", null))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("registrationFee"));
		assertThat(count("select count(*) from fee_rows where academic_year_id = ?", setup.yearId())).isZero();
		assertThat(count("select count(*) from fee_year_status where academic_year_id = ?", setup.yearId())).isZero();

		// 100.100 has only two significant decimals, so it is a valid amount of 100.10.
		add(client, setup, null, setup.standard(0), null, "100.100").andExpect(status().isCreated());
		assertThat(count("select count(*) from fee_rows where academic_year_id = ?", setup.yearId())).isEqualTo(1);
	}

	@Test
	void anAmountBeyondTenDigitsBeforeTheDecimalPointIsRefusedBesideTheField() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "RowHuge", LocalDate.of(3115, 4, 1), 1);
		SignedInClient client = SignedInClient.of(mvc);

		add(client, setup, null, setup.standard(0), null, "10000000000.00")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("amount"))
			.andExpect(jsonPath("$.fieldErrors[0].message").value(containsString("too large")));
		client.post("/api/fee-structures/" + setup.yearId() + "/rows",
				body(null, null, setup.standard(0), null, "1000", "10000000000", null))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("registrationFee"));
		assertThat(count("select count(*) from fee_rows where academic_year_id = ?", setup.yearId())).isZero();

		add(client, setup, null, setup.standard(0), null, "9999999999.99").andExpect(status().isCreated());
	}

	@Test
	void aMissingStandardOrAnUnknownOneIsRefused() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "RowStd", LocalDate.of(3107, 4, 1), 1);
		SignedInClient client = SignedInClient.of(mvc);

		client.post("/api/fee-structures/" + setup.yearId() + "/rows", "{\"structureVersion\":null,\"amount\":1000}")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("standardId"));
		add(client, setup, null, 999999999L, null, "1000")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("standardId"));
		add(client, setup, null, setup.standard(0), 999999999L, "1000")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("categoryId"));
	}

	@Test
	void anInactiveStandardOrCategoryIsRefusedForANewRowButAnExistingRowKeepsIt() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "RowInactive", LocalDate.of(3108, 4, 1), 2);
		SignedInClient client = SignedInClient.of(mvc);
		add(client, setup, null, setup.standard(0), setup.category(0), "1000").andExpect(status().isCreated());
		long rowId = onlyRowOf(setup);
		jdbc.update("update standards set active = false where id in (?, ?)", setup.standard(0), setup.standard(1));
		jdbc.update("update student_categories set active = false where id = ?", setup.category(0));

		add(client, setup, 1L, setup.standard(1), null, "2000")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("standardId"));
		// The existing row can still be edited with the values it holds.
		client.put("/api/fee-structures/" + setup.yearId() + "/rows/" + rowId,
				body(1L, 0L, setup.standard(0), setup.category(0), "1500", "null", null))
			.andExpect(status().isOk());
	}

	@Test
	void aStaleStructureVersionIsRefusedWithTheLatestStructure() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "RowStale", LocalDate.of(3109, 4, 1), 2);
		SignedInClient client = SignedInClient.of(mvc);
		add(client, setup, null, setup.standard(0), null, "1000").andExpect(status().isCreated());

		// The screen loaded the structure before the first row existed (version null); now it is 1.
		add(client, setup, null, setup.standard(1), null, "2000")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_VERSION"))
			.andExpect(jsonPath("$.current.version").value(1))
			.andExpect(jsonPath("$.current.rows.length()").value(1));
		add(client, setup, 7L, setup.standard(1), null, "2000").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_VERSION"));
		long rowId = onlyRowOf(setup);
		client.delete("/api/fee-structures/" + setup.yearId() + "/rows/" + rowId + "?version=0&structureVersion=0")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_VERSION"));

		assertThat(count("select count(*) from fee_rows where academic_year_id = ?", setup.yearId())).isEqualTo(1);
	}

	@Test
	void aStaleRowVersionIsRefused() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "RowStaleRow", LocalDate.of(3110, 4, 1), 1);
		SignedInClient client = SignedInClient.of(mvc);
		add(client, setup, null, setup.standard(0), null, "1000").andExpect(status().isCreated());
		long rowId = onlyRowOf(setup);

		client.put("/api/fee-structures/" + setup.yearId() + "/rows/" + rowId,
				body(1L, 5L, setup.standard(0), null, "1200", "null", null))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_VERSION"));
	}

	@Test
	void editingOrDeletingARowThatAnotherWindowDeletedAnswersStaleVersionNotNotFound() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "RowGone", LocalDate.of(3116, 4, 1), 2);
		SignedInClient client = SignedInClient.of(mvc);
		add(client, setup, null, setup.standard(0), null, "1000").andExpect(status().isCreated());
		add(client, setup, 1L, setup.standard(1), null, "2000").andExpect(status().isCreated());
		long gone = jdbc.queryForObject("select id from fee_rows where standard_id = ?", Long.class, setup.standard(1));
		// The other window deletes the second row; this window still holds structure version 2.
		client.delete("/api/fee-structures/" + setup.yearId() + "/rows/" + gone + "?version=0&structureVersion=2")
			.andExpect(status().isNoContent());

		client.put("/api/fee-structures/" + setup.yearId() + "/rows/" + gone,
				body(2L, 0L, setup.standard(1), null, "2500", "null", null))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_VERSION"));
		client.delete("/api/fee-structures/" + setup.yearId() + "/rows/" + gone + "?version=0&structureVersion=2")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_VERSION"));
	}

	@Test
	void aRowOfAnotherYearIsNotFound() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "RowOther", LocalDate.of(3111, 4, 1), 1);
		Setup other = FeeTestData.year(jdbc, "RowOtherB", LocalDate.of(3112, 4, 1), 1);
		SignedInClient client = SignedInClient.of(mvc);
		add(client, other, null, other.standard(0), null, "1000").andExpect(status().isCreated());
		long rowId = onlyRowOf(other);

		client.put("/api/fee-structures/" + setup.yearId() + "/rows/" + rowId,
				body(null, null, other.standard(0), null, "1200", "null", null)) // the year has no structure yet
			.andExpect(status().isNotFound());
		client.delete("/api/fee-structures/" + setup.yearId() + "/rows/" + rowId + "?version=0")
			.andExpect(status().isNotFound());
	}

	@Test
	void aFinalisedYearChangesNothingWithoutConfirmationAndReportsTheStudentCount() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "RowFinal", LocalDate.of(3113, 4, 1), 2);
		long rowId = FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(0), null, "1000", null, null);
		FeeTestData.setStatus(jdbc, setup.yearId(), true);
		FeeTestData.student(jdbc, setup, 1, 0, 0);
		FeeTestData.student(jdbc, setup, 2, 0, 1);
		SignedInClient client = SignedInClient.of(mvc);
		long audits = count("select count(*) from audit_log where area = 'Fees'");

		add(client, setup, 0L, setup.standard(1), null, "2000")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("REVISE_CONFIRMATION_REQUIRED"))
			.andExpect(jsonPath("$.current.studentCount").value(2));
		client.put("/api/fee-structures/" + setup.yearId() + "/rows/" + rowId,
				body(0L, 0L, setup.standard(0), null, "1200", "null", null))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("REVISE_CONFIRMATION_REQUIRED"));
		client.delete("/api/fee-structures/" + setup.yearId() + "/rows/" + rowId + "?version=0&structureVersion=0")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("REVISE_CONFIRMATION_REQUIRED"));

		assertThat(count("select count(*) from fee_rows where academic_year_id = ?", setup.yearId())).isEqualTo(1);
		assertThat(jdbc.queryForObject("select amount from fee_rows where id = ?", BigDecimal.class, rowId))
			.isEqualByComparingTo("1000");
		assertThat(jdbc.queryForObject("select version from fee_year_status where academic_year_id = ?", Long.class,
				setup.yearId())).isZero();
		assertThat(count("select count(*) from audit_log where area = 'Fees'")).isEqualTo(audits);
	}

	@Test
	void aStaleStructureVersionIsCheckedBeforeTheReviseConfirmation() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "RowFinalStale", LocalDate.of(3114, 4, 1), 2);
		FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(0), null, "1000", null, null);
		FeeTestData.setStatus(jdbc, setup.yearId(), true);

		add(SignedInClient.of(mvc), setup, null, setup.standard(1), null, "2000")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_VERSION"))
			.andExpect(jsonPath("$.current.status").value("Finalised"));
	}

}
