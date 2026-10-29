package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.FeeTestData.Fx;
import com.admin.management.support.FeeTestData.Setup;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.ReceiptApi;
import com.admin.management.support.SignedInClient;

/** The developer runbook voids a receipt without changing it; the test runs the runbook's own SQL (FR-022). */
@Covers("FR-022")
class VoidReceiptIT extends IntegrationTest {

	static final String TODAY = "3691-10-01";

	static final Path RUNBOOK = Paths.get("..", "docs", "runbooks", "correct-receipt.md");

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	Setup setup;

	SignedInClient client;

	int students;

	@BeforeAll
	void seed() {
		setup = FeeTestData.year(jdbc, "VoidIT", LocalDate.of(3691, 4, 1), 1);
		FeeTestData.setStatus(jdbc, setup.yearId(), true);
	}

	@BeforeEach
	void today() throws Exception {
		clock.setDate(LocalDate.of(3691, 10, 1));
		client = SignedInClient.of(mvc);
	}

	Fx student() {
		return FeeTestData.studentWithAccount(jdbc, setup, 939000 + (++students), "1000.00", null);
	}

	private String runbook() throws IOException {
		return Files.readString(RUNBOOK);
	}

	/** The block marked {@code -- runbook:void} with the three placeholders filled in. */
	private String voidSql(long serialNo, String reason, String name) throws IOException {
		Matcher m = Pattern.compile("```sql\\R-- runbook:void\\R(.*?)\\R```", Pattern.DOTALL).matcher(runbook());
		assertThat(m.find()).as("the runbook has a sql block marked -- runbook:void").isTrue();
		return m.group(1)
			.replace("<serial_no>", Long.toString(serialNo))
			.replace("<reason>", reason)
			.replace("<developer name>", name);
	}

	private void run(long serialNo, String reason, String name) throws IOException {
		jdbc.queryForList(voidSql(serialNo, reason, name));
	}

	@Test
	void theRunbookBlockWritesTheMarkAndTheAuditRowAndLeavesTheReceiptUnchanged() throws Exception {
		Fx s = student();
		long serial = FeeTestData.cashReceipt(jdbc, setup, s, "V-1", LocalDate.of(3691, 10, 1), "0", "400.00");
		Map<String, Object> before = jdbc.queryForMap("select * from receipts where serial_no = ?", serial);

		run(serial, "Wrong student", "Dev One");

		Map<String, Object> mark = jdbc.queryForMap("select * from receipt_marks where receipt_serial_no = ?", serial);
		assertThat(mark.get("kind")).isEqualTo("DEVELOPER_VOID");
		assertThat(mark.get("reason")).isEqualTo("Wrong student");
		assertThat(mark.get("amount_added_back")).hasToString("400.00");
		assertThat(mark.get("marked_by")).isNull();
		Map<String, Object> audit = jdbc.queryForMap("select * from audit_log where entity = ?", "Receipt#" + serial);
		assertThat(audit.get("area")).isEqualTo("Fees");
		assertThat(audit.get("action")).isEqualTo("Edit");
		assertThat(audit.get("reason")).isEqualTo("Wrong student");
		assertThat(String.valueOf(audit.get("changes"))).contains("Dev One").contains("VOIDED").contains("400.00");
		assertThat(jdbc.queryForMap("select * from receipts where serial_no = ?", serial)).isEqualTo(before);
	}

	@Test
	void aSecondCallAnEmptyReasonAnEmptyNameAndAnUnknownSerialAllFail() throws Exception {
		Fx s = student();
		long serial = FeeTestData.cashReceipt(jdbc, setup, s, "V-2", LocalDate.of(3691, 10, 1), "0", "100.00");
		long other = FeeTestData.cashReceipt(jdbc, setup, s, "V-3", LocalDate.of(3691, 10, 1), "0", "100.00");

		assertThatThrownBy(() -> run(other, "  ", "Dev One")).isInstanceOf(DataAccessException.class)
			.hasMessageContaining("a reason is required");
		assertThatThrownBy(() -> run(other, "Reason", " ")).isInstanceOf(DataAccessException.class)
			.hasMessageContaining("name of the person");
		assertThatThrownBy(() -> run(-5, "Reason", "Dev One")).isInstanceOf(DataAccessException.class)
			.hasMessageContaining("no receipt");
		run(serial, "Once", "Dev One");
		assertThatThrownBy(() -> run(serial, "Twice", "Dev One")).isInstanceOf(DataAccessException.class)
			.hasMessageContaining("already marked");

		assertThat(jdbc.queryForObject("select count(*) from receipt_marks where receipt_serial_no in (?, ?)",
				Integer.class, serial, other)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from audit_log where entity in (?, ?)", Integer.class,
				"Receipt#" + serial, "Receipt#" + other)).isEqualTo(1);
	}

	@Test
	void theBalanceAndTheReceiptStatusChangeAndTheNumberCanBeEnteredAgain() throws Exception {
		Fx s = student();
		long serial = ReceiptApi.serial(ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("V-4", TODAY, "300.00"))
			.andExpect(status().isCreated()));
		assertThat(FeeTestData.balance(jdbc, s.enrollmentId()).paidYearFee()).isEqualByComparingTo("300.00");
		String tokenBefore = ReceiptApi.token(client, s.studentId());

		run(serial, "Typed twice", "Dev One");

		assertThat(FeeTestData.balance(jdbc, s.enrollmentId()).paidYearFee()).isEqualByComparingTo("0");
		assertThat(ReceiptApi.token(client, s.studentId())).isNotEqualTo(tokenBefore);
		client.get("/api/receipts/" + serial)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("VOIDED"))
			.andExpect(jsonPath("$.statusReason").value("Typed twice"));
		long corrected = ReceiptApi.serial(ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("V-4", TODAY, "250.00"))
			.andExpect(status().isCreated()));
		assertThat(corrected).isGreaterThan(serial);
		assertThat(jdbc.queryForObject("select count(*) from receipts where receipt_no = 'V-4'", Integer.class))
			.isEqualTo(2);
		assertThat(FeeTestData.balance(jdbc, s.enrollmentId()).paidYearFee()).isEqualByComparingTo("250.00");
		client.get("/api/receipts/" + serial).andExpect(jsonPath("$.status").value("VOIDED"));
	}

	@Test
	void theRunbookHasEachRequiredSection() throws Exception {
		String text = runbook();
		for (String heading : new String[] { "## When", "## Who", "## Backup", "## Steps", "## What is recorded",
				"## Check" }) {
			assertThat(text).as(heading).contains("\n" + heading + "\n");
		}
		assertThat(text).contains("pg_dump").contains("admin_management_db").contains("-- runbook:void");
	}

}
