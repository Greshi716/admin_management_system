package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

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
 * Test plan rows FR-004 (N3, N4, B2, B3, B4, F3), FR-005 (B4 reading), FR-006 (B1) and EC-3, EC-5: Revise on a
 * year shaped like fixture S1. Rows: R1 Std 1 all categories 36,000.00 + 500.00; R2 Std 1 first category 40,000.00;
 * R3 Std 2 all categories 30,000.00; Std 3 has no row. Students A, B, C, D, E, F (F is in Std 3).
 */
@Covers("FR-004")
@Covers("FR-006")
class FeeReviseCasesIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	private record S1(Setup setup, long r1, long r2, long r3, int base) {

		String grNo(int k) {
			return "GR-" + (base + k);
		}
	}

	private S1 s1(String prefix, int yearNumber) throws Exception {
		Setup setup = FeeTestData.year(jdbc, prefix, LocalDate.of(yearNumber, 4, 1), 3);
		long r1 = FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(0), null, "36000", "500",
				LocalDate.of(yearNumber, 6, 10));
		long r2 = FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(0), setup.category(0), "40000", null, null);
		long r3 = FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(1), null, "30000", null, null);
		FeeTestData.setStatus(jdbc, setup.yearId(), false);
		int base = yearNumber * 100;
		FeeTestData.student(jdbc, setup, base + 1, 0, 0); // A: category row
		FeeTestData.student(jdbc, setup, base + 2, 0, 1); // B: falls back to R1
		FeeTestData.student(jdbc, setup, base + 3, 1, 0); // C
		FeeTestData.student(jdbc, setup, base + 4, 1, 0); // D
		FeeTestData.student(jdbc, setup, base + 5, 0, 1); // E: falls back to R1
		FeeTestData.student(jdbc, setup, base + 6, 2, 0); // F: Std 3, no row
		SignedInClient.of(mvc).post("/api/fee-structures/" + setup.yearId() + "/finalise", "{\"version\":0}")
			.andExpect(status().isOk());
		return new S1(setup, r1, r2, r3, base);
	}

	private String rows(S1 s) {
		return "/api/fee-structures/" + s.setup().yearId() + "/rows";
	}

	private long structureVersion(S1 s) {
		return jdbc.queryForObject("select version from fee_year_status where academic_year_id = ?", Long.class,
				s.setup().yearId());
	}

	private long rowVersion(long rowId) {
		return jdbc.queryForObject("select version from fee_rows where id = ?", Long.class, rowId);
	}

	private String body(Long structureVersion, Long rowVersion, long standardId, Long categoryId, String amount,
			String registrationFee) {
		return "{" + (rowVersion == null ? "" : "\"version\":" + rowVersion + ",") + "\"structureVersion\":"
				+ structureVersion + ",\"standardId\":" + standardId + ",\"categoryId\":" + categoryId + ",\"amount\":"
				+ amount + ",\"registrationFee\":" + registrationFee + ",\"dueDate\":null}";
	}

	private BigDecimal amountOf(S1 s, int k) {
		return jdbc.queryForObject(accountSql("a.amount"), BigDecimal.class, s.grNo(k), s.setup().yearId());
	}

	private BigDecimal registrationOf(S1 s, int k) {
		return jdbc.queryForObject(accountSql("a.registration_fee"), BigDecimal.class, s.grNo(k), s.setup().yearId());
	}

	private String accountSql(String column) {
		return "select " + column + """
				 from fee_accounts a join student_enrollments e on e.id = a.enrollment_id
				join students s on s.id = e.student_id where s.gr_no = ? and a.academic_year_id = ?
				""";
	}

	private long feeAudits() {
		return jdbc.queryForObject("select count(*) from audit_log where area = 'Fees'", Long.class);
	}

	@Test
	void addingARowThroughReviseGivesTheStudentWithoutAFeeItsAmountAndWarnsWithSix() throws Exception {
		S1 s = s1("RcAdd", 3721);
		SignedInClient client = SignedInClient.of(mvc);
		assertThat(amountOf(s, 6)).isNull();

		client.post(rows(s), body(structureVersion(s), null, s.setup().standard(2), null, "20000", "null"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("REVISE_CONFIRMATION_REQUIRED"))
			.andExpect(jsonPath("$.current.studentCount").value(6));
		assertThat(amountOf(s, 6)).isNull();

		client.post(rows(s) + "?confirm=true",
				body(structureVersion(s), null, s.setup().standard(2), null, "20000", "null"))
			.andExpect(status().isCreated());

		assertThat(amountOf(s, 6)).isEqualByComparingTo("20000");
		assertThat(jdbc.queryForObject("""
				select reason from audit_log where area = 'Fees' and action = 'Create' and entity like 'FeeRow#%'
				order by id desc limit 1
				""", String.class)).isEqualTo("Revise");
		assertThat(jdbc.queryForObject("""
				select changes::text from audit_log where area = 'Fees' and action = 'Create' and entity like 'FeeRow#%'
				order by id desc limit 1
				""", String.class)).contains("\"studentsRecalculated\": 6").contains("\"accountsChanged\": 1");
	}

	@Test
	void deletingARowThroughReviseLeavesItsStudentsWithoutAFeeAndTheEntryCountsFive() throws Exception {
		S1 s = s1("RcDel", 3722);

		SignedInClient.of(mvc).delete(rows(s) + "/" + s.r3() + "?version=0&structureVersion=" + structureVersion(s)
				+ "&confirm=true")
			.andExpect(status().isNoContent());

		assertThat(amountOf(s, 3)).isNull();
		assertThat(amountOf(s, 4)).isNull();
		assertThat(amountOf(s, 1)).isEqualByComparingTo("40000");
		assertThat(jdbc.queryForObject("""
				select changes::text from audit_log where entity = ? and action = 'Delete'
				""", String.class, "FeeRow#" + s.r3())).contains("\"studentsRecalculated\": 6")
			.contains("\"accountsChanged\": 2");
		assertThat(jdbc.queryForObject("select reason from audit_log where entity = ? and action = 'Delete'",
				String.class, "FeeRow#" + s.r3())).isEqualTo("Revise");
	}

	@Test
	void deletingTheCategoryRowMakesItsStudentFallBackToTheRowForAllCategories() throws Exception {
		S1 s = s1("RcFall", 3723);
		assertThat(amountOf(s, 1)).isEqualByComparingTo("40000");

		SignedInClient.of(mvc).delete(rows(s) + "/" + s.r2() + "?version=0&structureVersion=" + structureVersion(s)
				+ "&confirm=true")
			.andExpect(status().isNoContent());

		assertThat(amountOf(s, 1)).isEqualByComparingTo("36000");
		assertThat(registrationOf(s, 1)).isEqualByComparingTo("500");
	}

	@Test
	void aYearWithNoClassRecordsWarnsWithZeroAndSavesAfterConfirmation() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "RcEmpty", LocalDate.of(3724, 4, 1), 1);
		long row = FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(0), null, "1000", null, null);
		FeeTestData.setStatus(jdbc, setup.yearId(), false);
		SignedInClient client = SignedInClient.of(mvc);
		client.post("/api/fee-structures/" + setup.yearId() + "/finalise", "{\"version\":0}").andExpect(status().isOk());
		String url = "/api/fee-structures/" + setup.yearId() + "/rows/" + row;
		String json = "{\"version\":0,\"structureVersion\":1,\"standardId\":" + setup.standard(0)
				+ ",\"categoryId\":null,\"amount\":1500,\"registrationFee\":null,\"dueDate\":null}";

		client.put(url, json)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.current.studentCount").value(0));
		client.put(url + "?confirm=true", json).andExpect(status().isOk());

		assertThat(jdbc.queryForObject("select amount from fee_rows where id = ?", BigDecimal.class, row))
			.isEqualByComparingTo("1500");
		assertThat(jdbc.queryForObject("select changes::text from audit_log where entity = ? and action = 'Edit'",
				String.class, "FeeRow#" + row)).contains("\"studentsRecalculated\": 0");
	}

	@Test
	void finalisingAYearWithNoClassRecordsMakesNoAccountsAndRecordsZero() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "RcFinEmpty", LocalDate.of(3725, 4, 1), 1);
		FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(0), null, "1000", null, null);
		FeeTestData.setStatus(jdbc, setup.yearId(), false);

		SignedInClient.of(mvc).post("/api/fee-structures/" + setup.yearId() + "/finalise", "{\"version\":0}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("Finalised"));

		assertThat(jdbc.queryForObject("select count(*) from fee_accounts where academic_year_id = ?", Long.class,
				setup.yearId())).isZero();
		assertThat(jdbc.queryForObject("""
				select changes::text from audit_log where action = 'Finalise' and entity = ?
				""", String.class, "FeeStructure#" + setup.yearId())).contains("\"studentsRecalculated\": 0");
	}

	@Test
	void theWarningCountsOnlyTheClassRecordsOfThatYear() throws Exception {
		S1 s = s1("RcCount", 3726);
		S1 other = s1("RcCountOther", 3727);

		SignedInClient.of(mvc)
			.put(rows(s) + "/" + s.r1(), body(structureVersion(s), 0L, s.setup().standard(0), null, "37000", "500"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.current.studentCount").value(6));
		assertThat(other.setup().yearId()).isNotEqualTo(s.setup().yearId());
	}

	@Test
	void aLeftStudentIsCountedAndKeepsHisAccountThroughRevise() throws Exception {
		S1 s = s1("RcLeft", 3728);
		jdbc.update("update students set status = 'Left' where gr_no = ?", s.grNo(5));
		SignedInClient client = SignedInClient.of(mvc);

		client.put(rows(s) + "/" + s.r1(), body(structureVersion(s), 0L, s.setup().standard(0), null, "37000", "500"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.current.studentCount").value(6));
		client.put(rows(s) + "/" + s.r1() + "?confirm=true",
				body(structureVersion(s), 0L, s.setup().standard(0), null, "37000", "500"))
			.andExpect(status().isOk());

		assertThat(amountOf(s, 5)).isEqualByComparingTo("37000");
		assertThat(amountOf(s, 2)).isEqualByComparingTo("37000");
		assertThat(amountOf(s, 1)).isEqualByComparingTo("40000");
	}

	@Test
	void aReviseThatCreatesADuplicateRecalculatesNothingAndWritesNoEntry() throws Exception {
		S1 s = s1("RcDup", 3729);
		long audits = feeAudits();
		long version = structureVersion(s);

		SignedInClient.of(mvc)
			.put(rows(s) + "/" + s.r2() + "?confirm=true",
					body(version, 0L, s.setup().standard(0), null, "40000", "null"))
			.andExpect(status().isConflict());

		assertThat(amountOf(s, 1)).isEqualByComparingTo("40000");
		assertThat(jdbc.queryForObject("select category_id is not null from fee_rows where id = ?", Boolean.class,
				s.r2())).isTrue();
		assertThat(structureVersion(s)).isEqualTo(version);
		assertThat(feeAudits()).isEqualTo(audits);
		assertThat(rowVersion(s.r2())).isZero();
	}

	@Test
	void aSecondConfirmedChangeWarnsAgainAndWritesItsOwnEntry() throws Exception {
		S1 s = s1("RcTwice", 3730);
		SignedInClient client = SignedInClient.of(mvc);
		client.put(rows(s) + "/" + s.r1() + "?confirm=true",
				body(structureVersion(s), 0L, s.setup().standard(0), null, "37000", "500"))
			.andExpect(status().isOk());

		client.put(rows(s) + "/" + s.r3(), body(structureVersion(s), 0L, s.setup().standard(1), null, "31000", "null"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.current.studentCount").value(6));
		client.put(rows(s) + "/" + s.r3() + "?confirm=true",
				body(structureVersion(s), 0L, s.setup().standard(1), null, "31000", "null"))
			.andExpect(status().isOk());

		assertThat(amountOf(s, 3)).isEqualByComparingTo("31000");
		assertThat(amountOf(s, 4)).isEqualByComparingTo("31000");
		assertThat(jdbc.queryForObject("""
				select count(*) from audit_log where reason = 'Revise' and entity in (?, ?)
				""", Long.class, "FeeRow#" + s.r1(), "FeeRow#" + s.r3())).isEqualTo(2);
	}

}
