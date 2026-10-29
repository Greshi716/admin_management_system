package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.FeeTestData.Fx;
import com.admin.management.support.FeeTestData.Setup;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.ReceiptApi;
import com.admin.management.support.SignedInClient;

/** Every saved receipt writes one Fees entry in the same transaction, money in full and unmasked (FR-021). */
@Covers("FR-021")
class ReceiptAuditIT extends IntegrationTest {

	static final String TODAY = "3641-10-01";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoSpyBean
	FeesAudit feesAudit;

	Setup setup;

	SignedInClient client;

	int students;

	@BeforeAll
	void seed() {
		setup = FeeTestData.year(jdbc, "AuditIT", LocalDate.of(3641, 4, 1), 1);
		FeeTestData.setStatus(jdbc, setup.yearId(), true);
	}

	@BeforeEach
	void today() throws Exception {
		clock.setDate(LocalDate.of(3641, 10, 1));
		client = SignedInClient.of(mvc);
	}

	@AfterEach
	void clean() {
		reset(feesAudit);
	}

	Fx student() {
		return FeeTestData.studentWithAccount(jdbc, setup, 934000 + (++students), "36500.00", "500.00");
	}

	private List<Map<String, Object>> entries(long serialNo) {
		return jdbc.queryForList("select * from audit_log where entity = ? order by id", "Receipt#" + serialNo);
	}

	@Test
	void aSavedReceiptWritesOneCreateEntryInAreaFeesWithMoneyInFullAndUnmasked() throws Exception {
		Fx s = student();

		long serial = ReceiptApi.serial(ReceiptApi.create(client, s.studentId(),
				"\"receiptNo\":\"A-1\",\"receiptDate\":\"" + TODAY + "\",\"registrationPaid\":500.00,\"feePaid\":10000.00,"
						+ "\"paymentMode\":\"CHEQUE\",\"chequeNo\":\"778899\",\"bankName\":\"Test Bank\","
						+ "\"chequeDate\":\"3641-10-01\""));

		List<Map<String, Object>> found = entries(serial);
		assertThat(found).hasSize(1);
		Map<String, Object> entry = found.get(0);
		assertThat(entry.get("area")).isEqualTo("Fees");
		assertThat(entry.get("action")).isEqualTo("Create");
		assertThat(entry.get("user_id")).isNotNull();
		String changes = String.valueOf(entry.get("changes"));
		assertThat(changes).contains("\"receiptNo\": \"A-1\"")
			.contains("\"registrationPaid\": \"500.00\"")
			.contains("\"feePaid\": \"10000.00\"")
			.contains("\"total\": \"10500.00\"")
			.contains("\"chequeNo\": \"778899\"")
			.contains("\"bankName\": \"Test Bank\"")
			.contains("\"mode\": \"CHEQUE\"")
			.doesNotContain("*");
	}

	@Test
	void aRefusedSaveWritesNoEntryAndNoReceipt() throws Exception {
		Fx s = student();
		long entriesBefore = jdbc.queryForObject("select count(*) from audit_log where area = 'Fees'", Long.class);

		ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("A-2", TODAY, "99999.00")).andExpect(status().isConflict());
		ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("A-2", "3641-11-01", "10.00"))
			.andExpect(status().isUnprocessableEntity());

		assertThat(jdbc.queryForObject("select count(*) from audit_log where area = 'Fees'", Long.class))
			.isEqualTo(entriesBefore);
		assertThat(jdbc.queryForObject("select count(*) from receipts where receipt_no = 'A-2'", Long.class)).isZero();
	}

	@Test
	void aFailingAuditWriteLeavesNoReceiptAndTheSerialNoIsNotReused() throws Exception {
		Fx s = student();
		long first = ReceiptApi.serial(ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("A-3", TODAY, "10.00")));
		String token = ReceiptApi.token(client, s.studentId());
		doThrow(new IllegalStateException("audit is down")).when(feesAudit).receiptCreated(any());

		assertThatThrownBy(() -> ReceiptApi.createWithToken(client, s.studentId(), token, ReceiptApi.cash("A-4", TODAY, "10.00")))
			.hasMessageContaining("audit is down");

		assertThat(jdbc.queryForObject("select count(*) from receipts where receipt_no = 'A-4'", Long.class)).isZero();
		assertThat(ReceiptApi.token(client, s.studentId())).isEqualTo(token);
		reset(feesAudit);
		long next = ReceiptApi.serial(ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("A-4", TODAY, "10.00")));
		assertThat(next).isGreaterThan(first + 1);
	}

}
