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
import java.util.Map;

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

/** Finalising a year's fee structure: the status, the fee account of every class record, the audit entry. */
@Covers("FR-002")
@Covers("FR-003")
@Covers("FR-005")
@Covers("FR-006")
@Covers("FR-012")
class FeeFinaliseIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoSpyBean
	AuditService audit;

	private String finalise(Setup setup, Long version) {
		return "/api/fee-structures/" + setup.yearId() + "/finalise";
	}

	private Map<String, Object> account(long enrollmentId) {
		return jdbc.queryForMap("select amount, registration_fee, due_date from fee_accounts where enrollment_id = ?",
				enrollmentId);
	}

	private long accounts(Setup setup) {
		return jdbc.queryForObject("select count(*) from fee_accounts where academic_year_id = ?", Long.class,
				setup.yearId());
	}

	@Test
	void finalisingGivesEveryClassRecordItsFeeFromTheStructure() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "FinRule", LocalDate.of(3201, 4, 1), 3);
		FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(0), null, "36000", "500", LocalDate.of(3201, 6, 30));
		FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(0), setup.category(0), "40000", null,
				LocalDate.of(3201, 6, 15));
		FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(1), null, "28000.50", null, null);
		FeeTestData.setStatus(jdbc, setup.yearId(), false);
		long categoryRow = FeeTestData.student(jdbc, setup, 1, 0, 0);
		long fallbackRow = FeeTestData.student(jdbc, setup, 2, 0, 1);
		long plain = FeeTestData.student(jdbc, setup, 3, 1, 0);
		long noRow = FeeTestData.student(jdbc, setup, 4, 2, 0);
		long left = FeeTestData.student(jdbc, setup, 5, 0, 1);
		jdbc.update("update students set status = 'Left' where id = (select student_id from student_enrollments where id = ?)",
				left);

		SignedInClient.of(mvc).post(finalise(setup, 0L), "{\"version\":0}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("Finalised"))
			.andExpect(jsonPath("$.version").value(1))
			.andExpect(jsonPath("$.studentCount").value(5));

		assertThat(accounts(setup)).isEqualTo(5);
		Map<String, Object> category = account(categoryRow);
		assertThat((BigDecimal) category.get("amount")).isEqualByComparingTo("40000");
		assertThat((BigDecimal) category.get("registration_fee")).isEqualByComparingTo("500");
		assertThat(category.get("due_date").toString()).isEqualTo("3201-06-15");
		Map<String, Object> fallback = account(fallbackRow);
		assertThat((BigDecimal) fallback.get("amount")).isEqualByComparingTo("36000");
		assertThat((BigDecimal) fallback.get("registration_fee")).isEqualByComparingTo("500");
		assertThat(fallback.get("due_date").toString()).isEqualTo("3201-06-30");
		assertThat((BigDecimal) account(plain).get("amount")).isEqualByComparingTo("28000.50");
		assertThat(account(plain).get("registration_fee")).isNull();
		Map<String, Object> notSet = account(noRow);
		assertThat(notSet.get("amount")).isNull();
		assertThat(notSet.get("registration_fee")).isNull();
		assertThat(notSet.get("due_date")).isNull();
		assertThat((BigDecimal) account(left).get("amount")).isEqualByComparingTo("36000");
		assertThat(jdbc.queryForObject("select status from fee_year_status where academic_year_id = ?", String.class,
				setup.yearId())).isEqualTo("Finalised");
		assertThat(jdbc.queryForObject("select finalised_at is not null from fee_year_status where academic_year_id = ?",
				Boolean.class, setup.yearId())).isTrue();
	}

	@Test
	void theFinaliseAuditEntryHoldsTheCounts() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "FinAudit", LocalDate.of(3202, 4, 1), 2);
		FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(0), null, "1000", null, null);
		FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(1), null, "2000", null, null);
		FeeTestData.setStatus(jdbc, setup.yearId(), false);
		FeeTestData.student(jdbc, setup, 11, 0, 0);
		FeeTestData.student(jdbc, setup, 12, 1, 0);
		FeeTestData.student(jdbc, setup, 13, 1, 1);

		SignedInClient.of(mvc).post(finalise(setup, 0L), "{\"version\":0}").andExpect(status().isOk());

		List<String> entries = jdbc.queryForList(
				"select changes::text from audit_log where area = 'Fees' and action = 'Finalise' and entity = ?",
				String.class, "FeeStructure#" + setup.yearId());
		assertThat(entries).hasSize(1);
		assertThat(entries.get(0)).contains("\"studentsRecalculated\": 3")
			.contains("\"accountsChanged\": 3")
			.contains("\"rows\": 2");
	}

	@Test
	void aStructureWithNoRowsCannotBeFinalised() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "FinEmpty", LocalDate.of(3203, 4, 1), 1);
		SignedInClient client = SignedInClient.of(mvc);

		// No status row at all: the version the screen holds is null.
		client.post(finalise(setup, null), "{\"version\":null}").andExpect(status().isUnprocessableEntity());
		FeeTestData.setStatus(jdbc, setup.yearId(), false);
		client.post(finalise(setup, 0L), "{\"version\":0}")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].message").value("Add at least one fee row before finalising."));

		assertThat(jdbc.queryForObject("select status from fee_year_status where academic_year_id = ?", String.class,
				setup.yearId())).isEqualTo("Draft");
		assertThat(accounts(setup)).isZero();
	}

	@Test
	void aFinalisedYearCannotBeFinalisedAgainAndRowsCannotBeChangedWithoutConfirmation() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "FinTwice", LocalDate.of(3204, 4, 1), 1);
		FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(0), null, "1000", null, null);
		FeeTestData.setStatus(jdbc, setup.yearId(), false);
		SignedInClient client = SignedInClient.of(mvc);
		client.post(finalise(setup, 0L), "{\"version\":0}").andExpect(status().isOk());

		client.post(finalise(setup, 1L), "{\"version\":1}")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].message").value("This fee structure is already finalised."));
		client.post("/api/fee-structures/" + setup.yearId() + "/rows",
				"{\"structureVersion\":1,\"standardId\":" + setup.standard(0) + ",\"amount\":5}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("REVISE_CONFIRMATION_REQUIRED"));
	}

	@Test
	void aStaleVersionIsRefusedAndNothingIsFinalised() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "FinStale", LocalDate.of(3205, 4, 1), 1);
		FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(0), null, "1000", null, null);
		FeeTestData.setStatus(jdbc, setup.yearId(), false);
		FeeTestData.student(jdbc, setup, 21, 0, 0);

		SignedInClient.of(mvc).post(finalise(setup, 5L), "{\"version\":5}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_VERSION"))
			.andExpect(jsonPath("$.current.status").value("Draft"));

		assertThat(accounts(setup)).isZero();
	}

	@Test
	void twoThousandClassRecordsAreFinalisedInUnderTenSeconds() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "FinBulk", LocalDate.of(3206, 4, 1), 4);
		for (int s = 0; s < 4; s++) {
			FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(s), null, String.valueOf(10000 * (s + 1)), "250",
					null);
			FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(s), setup.category(1), "9000.25", null, null);
		}
		FeeTestData.setStatus(jdbc, setup.yearId(), false);
		FeeTestData.seedClassRecords(jdbc, setup, "FINBULK", 2000);
		SignedInClient client = SignedInClient.of(mvc);

		long start = System.nanoTime();
		client.post(finalise(setup, 0L), "{\"version\":0}").andExpect(status().isOk());
		Duration took = Duration.ofNanos(System.nanoTime() - start);

		assertThat(took).as("Finalise of 2000 class records took %d ms", took.toMillis()).isLessThan(Duration.ofSeconds(10));
		assertThat(accounts(setup)).isEqualTo(2000);
		assertThat(jdbc.queryForObject("select count(*) from fee_accounts where academic_year_id = ? and amount is null",
				Long.class, setup.yearId())).isZero();
	}

	@Test
	void aFinaliseWhoseAuditEntryCannotBeSavedLeavesTheYearADraft() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "FinRollback", LocalDate.of(3207, 4, 1), 1);
		FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(0), null, "1000", null, null);
		FeeTestData.setStatus(jdbc, setup.yearId(), false);
		FeeTestData.student(jdbc, setup, 31, 0, 0);
		SignedInClient client = SignedInClient.of(mvc);
		// Stubbed on the target, because a call through the proxy would meet the MANDATORY check first.
		doThrow(new IllegalStateException("audit insert failed")).when(AopTestUtils.<AuditService>getUltimateTargetObject(audit))
			.record(any(), any(), any(), any(), any());

		assertThatThrownBy(() -> client.post(finalise(setup, 0L), "{\"version\":0}"))
			.hasMessageContaining("audit insert failed");

		assertThat(jdbc.queryForObject("select status from fee_year_status where academic_year_id = ?", String.class,
				setup.yearId())).isEqualTo("Draft");
		assertThat(accounts(setup)).isZero();
	}

}
