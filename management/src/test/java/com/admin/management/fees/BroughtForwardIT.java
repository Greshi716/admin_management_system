package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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

/** The amount of earlier years' dues a student brings forward: enter, change and clear it. */
@Covers("FR-007")
@Covers("FR-009")
@Covers("FR-011")
@Covers("FR-012")
class BroughtForwardIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoSpyBean
	AuditService audit;

	private Setup setup(String prefix, int yearNumber) {
		return FeeTestData.year(jdbc, prefix, LocalDate.of(yearNumber, 4, 1), 2);
	}

	private long studentIn(Setup setup, int n, int standardIndex) {
		long enrollment = FeeTestData.student(jdbc, setup, n, standardIndex, 0);
		return jdbc.queryForObject("select student_id from student_enrollments where id = ?", Long.class, enrollment);
	}

	private String url(long studentId) {
		return "/api/students/" + studentId + "/brought-forward";
	}

	private String body(Long version, String amount, String note) {
		return "{\"version\":" + version + ",\"amount\":" + amount + ",\"note\":"
				+ (note == null ? "null" : "\"" + note + "\"") + "}";
	}

	private List<String> auditChanges(long studentId) {
		return jdbc.queryForList(
				"select changes::text from audit_log where area = 'Fees' and entity = ? order by id",
				String.class, "BroughtForward#" + studentId);
	}

	private List<String> auditActions(long studentId) {
		return jdbc.queryForList("select action from audit_log where area = 'Fees' and entity = ? order by id",
				String.class, "BroughtForward#" + studentId);
	}

	@Test
	void enteringChangingAndClearingTheAmountEachSavesAndWritesAnAuditEntry() throws Exception {
		Setup setup = setup("BfFlow", 3501);
		long student = studentIn(setup, 350101, 0);
		SignedInClient client = SignedInClient.of(mvc);

		client.put(url(student), body(null, "2500", "Fees of 2025"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("\"amount\":2500.00")))
			.andExpect(jsonPath("$.note").value("Fees of 2025"))
			.andExpect(jsonPath("$.version").value(0));
		client.put(url(student), body(0L, "1000.50", "Fees of 2025"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("\"amount\":1000.50")))
			.andExpect(jsonPath("$.version").value(1));
		client.put(url(student), body(1L, "0", null))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("\"amount\":0.00")))
			.andExpect(jsonPath("$.note").doesNotExist());

		assertThat(jdbc.queryForObject("select count(*) from fee_brought_forward where student_id = ?", Integer.class,
				student)).isEqualTo(1); // cleared, never deleted
		assertThat(jdbc.queryForObject("select amount from fee_brought_forward where student_id = ?", BigDecimal.class,
				student)).isEqualByComparingTo("0");
		assertThat(auditActions(student)).containsExactly("Create", "Edit", "Edit");
		assertThat(auditChanges(student).get(0)).contains("2500.00").contains("\"noteChanged\": true");
		assertThat(auditChanges(student).get(1)).contains("2500.00").contains("1000.50");
		assertThat(auditChanges(student).get(2)).contains("1000.50").contains("0.00").contains("\"noteChanged\": true");
	}

	@Test
	void theAuditEntryNeverHoldsTheTextOfTheNote() throws Exception {
		Setup setup = setup("BfNote", 3502);
		long student = studentIn(setup, 350201, 0);

		SignedInClient.of(mvc).put(url(student), body(null, "100", "Secret reason text")).andExpect(status().isOk());

		assertThat(String.join(" ", auditChanges(student))).doesNotContain("Secret reason text");
		assertThat(jdbc.queryForObject("select count(*) from audit_log where changes::text like '%Secret reason%'",
				Integer.class)).isZero();
	}

	@Test
	void theFeesBlockAddsItToTheTotalDueAndStillShowsItWhenTheFeeIsNotSet() throws Exception {
		Setup setup = setup("BfDue", 3503);
		FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(0), null, "28000", "500", null);
		FeeTestData.setStatus(jdbc, setup.yearId(), false);
		long withFee = studentIn(setup, 350301, 0);
		long withoutFee = studentIn(setup, 350302, 1);
		SignedInClient client = SignedInClient.of(mvc);
		client.post("/api/fee-structures/" + setup.yearId() + "/finalise", "{\"version\":0}").andExpect(status().isOk());

		client.put(url(withFee), body(null, "2500", "Old")).andExpect(status().isOk());
		client.put(url(withoutFee), body(null, "700.25", null)).andExpect(status().isOk());

		client.get("/api/students/" + withFee + "/fees?yearId=" + setup.yearId())
			.andExpect(jsonPath("$.state").value("FEE_SET"))
			.andExpect(content().string(containsString("\"totalDue\":31000.00")));
		client.get("/api/students/" + withoutFee + "/fees?yearId=" + setup.yearId())
			.andExpect(jsonPath("$.state").value("FEE_NOT_SET"))
			.andExpect(content().string(containsString("\"totalDue\":700.25")));
	}

	@Test
	void anAmountWithMoreThanTwoDecimalsOrBelowZeroOrMissingIsRefusedBesideTheField() throws Exception {
		Setup setup = setup("BfBad", 3504);
		long student = studentIn(setup, 350401, 0);
		SignedInClient client = SignedInClient.of(mvc);

		client.put(url(student), body(null, "100.123", null))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("amount"));
		client.put(url(student), body(null, "-1", null))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("amount"));
		client.put(url(student), "{\"version\":null,\"note\":null}")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("amount"));

		assertThat(jdbc.queryForObject("select count(*) from fee_brought_forward where student_id = ?", Integer.class,
				student)).isZero();
		assertThat(auditActions(student)).isEmpty();
	}

	@Test
	void aNoteOf200CharactersIsAcceptedAndOneOf201IsRefused() throws Exception {
		Setup setup = setup("BfLen", 3505);
		long student = studentIn(setup, 350501, 0);
		SignedInClient client = SignedInClient.of(mvc);

		client.put(url(student), body(null, "10", "x".repeat(201)))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("note"));
		client.put(url(student), body(null, "10", "x".repeat(200))).andExpect(status().isOk());

		assertThat(jdbc.queryForObject("select length(note) from fee_brought_forward where student_id = ?",
				Integer.class, student)).isEqualTo(200);
	}

	@Test
	void aStaleVersionIsRefusedWithTheLatestValue() throws Exception {
		Setup setup = setup("BfStale", 3506);
		long student = studentIn(setup, 350601, 0);
		SignedInClient client = SignedInClient.of(mvc);
		client.put(url(student), body(null, "100", "First")).andExpect(status().isOk());
		client.put(url(student), body(0L, "200", "Second")).andExpect(status().isOk());

		client.put(url(student), body(0L, "300", "Third"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_VERSION"))
			.andExpect(content().string(containsString("\"amount\":200.00")));
		client.put(url(student), body(null, "300", "Third"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_VERSION"));

		assertThat(jdbc.queryForObject("select amount from fee_brought_forward where student_id = ?", BigDecimal.class,
				student)).isEqualByComparingTo("200");
	}

	@Test
	void aSaveThatChangesNothingWritesNoEntryAndKeepsTheVersion() throws Exception {
		Setup setup = setup("BfSame", 3507);
		long student = studentIn(setup, 350701, 0);
		SignedInClient client = SignedInClient.of(mvc);
		client.put(url(student), body(null, "100", "Same")).andExpect(status().isOk());

		client.put(url(student), body(0L, "100.00", "Same"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version").value(0));

		assertThat(auditActions(student)).containsExactly("Create");
	}

	@Test
	void aChangedNoteOnlyIsRecordedAsNoteChangedWithoutAmounts() throws Exception {
		Setup setup = setup("BfNoteOnly", 3508);
		long student = studentIn(setup, 350801, 0);
		SignedInClient client = SignedInClient.of(mvc);
		client.put(url(student), body(null, "100", "One")).andExpect(status().isOk());

		client.put(url(student), body(0L, "100", "Two")).andExpect(status().isOk());

		assertThat(auditActions(student)).containsExactly("Create", "Edit");
		assertThat(auditChanges(student).get(1)).contains("\"noteChanged\": true").doesNotContain("amount");
	}

	@Test
	void anUnknownStudentIsNotFound() throws Exception {
		SignedInClient.of(mvc).put(url(987654321L), body(null, "100", null)).andExpect(status().isNotFound());
	}

	@Test
	void finalisingAndRevisingNeverChangeIt() throws Exception {
		Setup setup = setup("BfKeep", 3509);
		FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(0), null, "28000", null, null);
		FeeTestData.setStatus(jdbc, setup.yearId(), false);
		long student = studentIn(setup, 350901, 0);
		SignedInClient client = SignedInClient.of(mvc);
		client.put(url(student), body(null, "2500", "Keep")).andExpect(status().isOk());

		client.post("/api/fee-structures/" + setup.yearId() + "/finalise", "{\"version\":0}").andExpect(status().isOk());
		long row = jdbc.queryForObject("select id from fee_rows where academic_year_id = ?", Long.class, setup.yearId());
		client.put("/api/fee-structures/" + setup.yearId() + "/rows/" + row + "?confirm=true",
				"{\"version\":0,\"structureVersion\":1,\"standardId\":" + setup.standard(0)
						+ ",\"categoryId\":null,\"amount\":29000,\"registrationFee\":null,\"dueDate\":null}")
			.andExpect(status().isOk());

		assertThat(jdbc.queryForObject("select amount from fee_brought_forward where student_id = ?", BigDecimal.class,
				student)).isEqualByComparingTo("2500");
		assertThat(jdbc.queryForObject("select version from fee_brought_forward where student_id = ?", Long.class,
				student)).isZero();
		client.get("/api/students/" + student + "/fees?yearId=" + setup.yearId())
			.andExpect(content().string(not(containsString("receipt"))))
			.andExpect(content().string(containsString("\"totalDue\":31500.00")));
	}

	@Test
	void aFailingAuditWriteLeavesNothingSaved() throws Exception {
		Setup setup = setup("BfRoll", 3510);
		long student = studentIn(setup, 351001, 0);
		SignedInClient client = SignedInClient.of(mvc);
		doThrow(new IllegalStateException("audit insert failed")).when(AopTestUtils.<AuditService>getUltimateTargetObject(audit))
			.record(any(), any(), any(), any(), any());

		assertThatThrownBy(() -> client.put(url(student), body(null, "100", "x"))).hasMessageContaining("audit insert failed");

		assertThat(jdbc.queryForObject("select count(*) from fee_brought_forward where student_id = ?", Integer.class,
				student)).isZero();
	}

}
