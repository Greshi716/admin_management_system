package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.admin.management.audit.AuditService;
import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.FeeTestData.Setup;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignedInClient;

/** Revising a finalised structure: one confirmed row change recalculates every class record of the year. */
@Covers("FR-003")
@Covers("FR-004")
@Covers("FR-005")
@Covers("FR-006")
@Covers("FR-012")
class FeeReviseIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoSpyBean
	AuditService audit;

	/** Two standards; Std 1 has 36,000.00 + 500.00 registration for all categories. Finalised, with students. */
	private Setup finalisedYear(String prefix, int yearNumber, int studentsStd1, int studentsStd2) throws Exception {
		Setup setup = FeeTestData.year(jdbc, prefix, LocalDate.of(yearNumber, 4, 1), 2);
		FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(0), null, "36000", "500", LocalDate.of(yearNumber, 6, 30));
		FeeTestData.setStatus(jdbc, setup.yearId(), false);
		for (int i = 0; i < studentsStd1; i++) {
			FeeTestData.student(jdbc, setup, yearNumber * 100 + i, 0, i % 2);
		}
		for (int i = 0; i < studentsStd2; i++) {
			FeeTestData.student(jdbc, setup, yearNumber * 100 + 50 + i, 1, 0);
		}
		SignedInClient.of(mvc).post("/api/fee-structures/" + setup.yearId() + "/finalise", "{\"version\":0}")
			.andExpect(status().isOk());
		return setup;
	}

	private String url(Setup setup, String rest) {
		return "/api/fee-structures/" + setup.yearId() + "/rows" + rest;
	}

	private String body(long structureVersion, Long rowVersion, long standardId, Long categoryId, String amount) {
		return "{" + (rowVersion == null ? "" : "\"version\":" + rowVersion + ",") + "\"structureVersion\":"
				+ structureVersion + ",\"standardId\":" + standardId + ",\"categoryId\":" + categoryId
				+ ",\"amount\":" + amount + ",\"registrationFee\":null,\"dueDate\":null}";
	}

	private long rowId(Setup setup, long standardId, Long categoryId) {
		return jdbc.queryForObject("""
				select id from fee_rows where academic_year_id = ? and standard_id = ?
				and category_id is not distinct from ?
				""", Long.class, setup.yearId(), standardId, categoryId);
	}

	private long structureVersion(Setup setup) {
		return jdbc.queryForObject("select version from fee_year_status where academic_year_id = ?", Long.class,
				setup.yearId());
	}

	private BigDecimal amountOf(Setup setup, String grNo) {
		return jdbc.queryForObject("""
				select a.amount from fee_accounts a join student_enrollments e on e.id = a.enrollment_id
				join students s on s.id = e.student_id where s.gr_no = ? and a.academic_year_id = ?
				""", BigDecimal.class, grNo, setup.yearId());
	}

	@Test
	void aConfirmedEditRecalculatesEveryClassRecordAndWritesOneReviseEntry() throws Exception {
		Setup setup = finalisedYear("RevEdit", 3501, 4, 2);
		long row = rowId(setup, setup.standard(0), null);
		long structure = structureVersion(setup);
		SignedInClient client = SignedInClient.of(mvc);

		client.put(url(setup, "/" + row + "?confirm=true"), body(structure, 0L, setup.standard(0), null, "40000"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version").value(1));

		assertThat(amountOf(setup, "GR-350100")).isEqualByComparingTo("40000");
		assertThat(jdbc.queryForObject("""
				select count(*) from fee_accounts where academic_year_id = ? and amount = 40000
				""", Long.class, setup.yearId())).isEqualTo(4);
		assertThat(jdbc.queryForObject("select version from fee_year_status where academic_year_id = ?", Long.class,
				setup.yearId())).isEqualTo(structure + 1);
		List<String> entries = jdbc.queryForList(
				"select changes::text from audit_log where entity = ? and action = 'Edit'", String.class, "FeeRow#" + row);
		assertThat(entries).hasSize(1);
		assertThat(entries.get(0)).contains("\"amount\": \"36000.00\"")
			.contains("\"amount\": \"40000.00\"")
			.contains("\"studentsRecalculated\": 6")
			.contains("\"accountsChanged\": 4");
		assertThat(jdbc.queryForObject("select reason from audit_log where entity = ? and action = 'Edit'", String.class,
				"FeeRow#" + row)).isEqualTo("Revise");
	}

	@Test
	void onlyAccountsWhoseValuesChangedAreWritten() throws Exception {
		Setup setup = finalisedYear("RevOnly", 3502, 3, 3);
		long before = jdbc.queryForObject("select count(*) from fee_accounts where academic_year_id = ?", Long.class,
				setup.yearId());
		// Std 2 has no row, so its accounts are "Fee not set"; adding a Std 2 row changes exactly those three.
		SignedInClient client = SignedInClient.of(mvc);
		jdbc.update("update fee_accounts set updated_at = timestamptz '2000-01-01 00:00:00+00' where academic_year_id = ?",
				setup.yearId());

		client.post(url(setup, "?confirm=true"), body(structureVersion(setup), null, setup.standard(1), null, "25000"))
			.andExpect(status().isCreated());

		assertThat(jdbc.queryForObject("""
				select count(*) from fee_accounts where academic_year_id = ? and updated_at > timestamptz '2001-01-01'
				""", Long.class, setup.yearId())).isEqualTo(3);
		assertThat(jdbc.queryForObject("select count(*) from fee_accounts where academic_year_id = ?", Long.class,
				setup.yearId())).isEqualTo(before);
		assertThat(jdbc.queryForObject("""
				select changes::text from audit_log where action = 'Create' and reason = 'Revise' and entity like 'FeeRow#%'
				order by id desc limit 1
				""", String.class)).contains("\"studentsRecalculated\": 6").contains("\"accountsChanged\": 3");
	}

	@Test
	void aConfirmedDeleteMakesTheFeeNotSetAndAnEditThatChangesNothingChangesNoAccount() throws Exception {
		Setup setup = finalisedYear("RevDelete", 3503, 2, 1);
		long row = rowId(setup, setup.standard(0), null);
		SignedInClient client = SignedInClient.of(mvc);

		client.delete(url(setup, "/" + row + "?version=0&structureVersion=" + structureVersion(setup) + "&confirm=true"))
			.andExpect(status().isNoContent());

		assertThat(jdbc.queryForObject("select count(*) from fee_accounts where academic_year_id = ? and amount is null",
				Long.class, setup.yearId())).isEqualTo(3);
		assertThat(jdbc.queryForObject("select count(*) from fee_rows where academic_year_id = ?", Long.class,
				setup.yearId())).isZero();
		assertThat(jdbc.queryForObject(
				"select changes::text from audit_log where entity = ? and action = 'Delete'", String.class,
				"FeeRow#" + row)).contains("\"amount\": \"36000.00\"").contains("\"accountsChanged\": 2");
	}

	@Test
	void withoutConfirmationNothingChangesAndTheStudentCountIsReported() throws Exception {
		Setup setup = finalisedYear("RevNoConfirm", 3504, 2, 1);
		long row = rowId(setup, setup.standard(0), null);
		SignedInClient client = SignedInClient.of(mvc);
		long structure = structureVersion(setup);
		long entries = jdbc.queryForObject("select count(*) from audit_log where area = 'Fees'", Long.class);

		client.put(url(setup, "/" + row), body(structure, 0L, setup.standard(0), null, "40000"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("REVISE_CONFIRMATION_REQUIRED"))
			.andExpect(jsonPath("$.current.studentCount").value(3));

		assertThat(amountOf(setup, "GR-350400")).isEqualByComparingTo("36000");
		assertThat(structureVersion(setup)).isEqualTo(structure);
		assertThat(jdbc.queryForObject("select count(*) from audit_log where area = 'Fees'", Long.class))
			.isEqualTo(entries);
	}

	@Test
	void aStaleStructureVersionWinsOverTheWarningAndTheConfirmation() throws Exception {
		Setup setup = finalisedYear("RevStale", 3505, 1, 0);
		long row = rowId(setup, setup.standard(0), null);

		SignedInClient.of(mvc)
			.put(url(setup, "/" + row + "?confirm=true"), body(structureVersion(setup) + 7, 0L, setup.standard(0), null, "40000"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_VERSION"));

		assertThat(amountOf(setup, "GR-350500")).isEqualByComparingTo("36000");
	}

	@Test
	void twoThousandClassRecordsAreRevisedInUnderTenSeconds() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "RevBulk", LocalDate.of(3506, 4, 1), 4);
		for (int s = 0; s < 4; s++) {
			FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(s), null, String.valueOf(10000 * (s + 1)), "250", null);
		}
		FeeTestData.setStatus(jdbc, setup.yearId(), false);
		FeeTestData.seedClassRecords(jdbc, setup, "REVBULK", 2000);
		SignedInClient client = SignedInClient.of(mvc);
		client.post("/api/fee-structures/" + setup.yearId() + "/finalise", "{\"version\":0}").andExpect(status().isOk());
		long row = rowId(setup, setup.standard(0), null);

		long start = System.nanoTime();
		client.put(url(setup, "/" + row + "?confirm=true"), body(structureVersion(setup), 0L, setup.standard(0), null, "11000"))
			.andExpect(status().isOk());
		Duration took = Duration.ofNanos(System.nanoTime() - start);

		assertThat(took).as("Revise of 2000 class records took %d ms", took.toMillis()).isLessThan(Duration.ofSeconds(10));
		assertThat(jdbc.queryForObject("select count(*) from fee_accounts where academic_year_id = ? and amount = 11000",
				Long.class, setup.yearId())).isEqualTo(500);
	}

	@Test
	void aReviseWhoseAuditEntryCannotBeSavedChangesNothing() throws Exception {
		Setup setup = finalisedYear("RevRollback", 3507, 2, 0);
		long row = rowId(setup, setup.standard(0), null);
		long structure = structureVersion(setup);
		SignedInClient client = SignedInClient.of(mvc);
		// Stubbed on the target, because a call through the proxy would meet the MANDATORY check first.
		doThrow(new IllegalStateException("audit insert failed")).when(AopTestUtils.<AuditService>getUltimateTargetObject(audit))
			.record(any(), any(), any(), any(), any());

		assertThatThrownBy(() -> client.put(url(setup, "/" + row + "?confirm=true"),
				body(structure, 0L, setup.standard(0), null, "40000"))).hasMessageContaining("audit insert failed");

		assertThat(jdbc.queryForObject("select amount from fee_rows where id = ?", BigDecimal.class, row))
			.isEqualByComparingTo("36000");
		assertThat(amountOf(setup, "GR-350700")).isEqualByComparingTo("36000");
		assertThat(structureVersion(setup)).isEqualTo(structure);
	}

}
