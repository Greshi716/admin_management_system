package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

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

/** A save made on a screen older than the server's balance is refused with BALANCE_CHANGED and changes nothing (FR-016). */
@Covers("FR-016")
class BalanceChangedIT extends IntegrationTest {

	static final String TODAY = "3661-10-01";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	Setup setup;

	SignedInClient client;

	int students;

	@BeforeAll
	void seed() {
		setup = FeeTestData.year(jdbc, "BalIT", LocalDate.of(3661, 4, 1), 1);
		FeeTestData.setStatus(jdbc, setup.yearId(), true);
	}

	@BeforeEach
	void today() throws Exception {
		clock.setDate(LocalDate.of(3661, 10, 1));
		client = SignedInClient.of(mvc);
	}

	Fx student() {
		return FeeTestData.studentWithAccount(jdbc, setup, 936000 + (++students), "10000.00", null);
	}

	private long receipts(long studentId) {
		return jdbc.queryForObject("select count(*) from receipts where student_id = ?", Long.class, studentId);
	}

	@Test
	void aStaleTokenAfterAnotherReceiptIsRefusedWithTheFreshContextAndNothingIsSaved() throws Exception {
		Fx s = student();
		String stale = ReceiptApi.token(client, s.studentId());
		ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("B-1", TODAY, "100.00")).andExpect(status().isCreated());

		ReceiptApi.createWithToken(client, s.studentId(), stale, ReceiptApi.cash("B-2", TODAY, "100.00"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("BALANCE_CHANGED"))
			.andExpect(jsonPath("$.current.balanceVersion").value(ReceiptApi.token(client, s.studentId())))
			.andExpect(jsonPath("$.current.balance.paid").value(100.00));
		assertThat(receipts(s.studentId())).isEqualTo(1);

		String latest = ReceiptApi.token(client, s.studentId());
		ReceiptApi.createWithToken(client, s.studentId(), latest, ReceiptApi.cash("B-2", TODAY, "100.00"))
			.andExpect(status().isCreated());
	}

	@Test
	void aStaleTokenAfterAMarkIsRefused() throws Exception {
		Fx s = student();
		long cheque = FeeTestData.chequeReceipt(jdbc, setup, s, "B-3", LocalDate.of(3661, 10, 1), "100.00");
		String stale = ReceiptApi.token(client, s.studentId());
		FeeTestData.markNotCleared(jdbc, cheque, "Bounced");

		ReceiptApi.createWithToken(client, s.studentId(), stale, ReceiptApi.cash("B-4", TODAY, "100.00"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("BALANCE_CHANGED"));
		assertThat(receipts(s.studentId())).isEqualTo(1);
	}

	@Test
	void aStaleTokenAfterABroughtForwardChangeIsRefused() throws Exception {
		Fx s = student();
		String stale = ReceiptApi.token(client, s.studentId());
		jdbc.update("insert into fee_brought_forward (student_id, amount, updated_at) values (?, 0.00, now())",
				s.studentId());

		ReceiptApi.createWithToken(client, s.studentId(), stale, ReceiptApi.cash("B-5", TODAY, "100.00"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("BALANCE_CHANGED"));
		String second = ReceiptApi.token(client, s.studentId());
		jdbc.update("update fee_brought_forward set version = version + 1 where student_id = ?", s.studentId());
		ReceiptApi.createWithToken(client, s.studentId(), second, ReceiptApi.cash("B-5", TODAY, "100.00"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("BALANCE_CHANGED"));
		assertThat(receipts(s.studentId())).isZero();
	}

	@Test
	void aStaleTokenAfterAReviseOfTheAccountIsRefused() throws Exception {
		Fx s = student();
		String stale = ReceiptApi.token(client, s.studentId());
		jdbc.update("update fee_accounts set amount = 9000.00, updated_at = clock_timestamp() where enrollment_id = ?",
				s.enrollmentId());

		ReceiptApi.createWithToken(client, s.studentId(), stale, ReceiptApi.cash("B-6", TODAY, "100.00"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("BALANCE_CHANGED"));
	}

	@Test
	void anotherStudentsReceiptDoesNotStaleThisStudentsToken() throws Exception {
		Fx a = student();
		Fx b = student();
		String token = ReceiptApi.token(client, a.studentId());

		ReceiptApi.create(client, b.studentId(), ReceiptApi.cash("B-7", TODAY, "100.00")).andExpect(status().isCreated());

		ReceiptApi.createWithToken(client, a.studentId(), token, ReceiptApi.cash("B-8", TODAY, "100.00"))
			.andExpect(status().isCreated());
	}

	@Test
	void aMissingTokenIsAFieldErrorNotASave() throws Exception {
		Fx s = student();
		client.post("/api/receipts", "{\"studentId\":" + s.studentId() + ",\"receiptType\":\"CURRENT_YEAR\","
				+ ReceiptApi.cash("B-9", TODAY, "100.00") + "}")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("balanceVersion"));
		assertThat(receipts(s.studentId())).isZero();
	}

}
