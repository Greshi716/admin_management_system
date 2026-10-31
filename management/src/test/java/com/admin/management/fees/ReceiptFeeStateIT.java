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

/** A current-year receipt needs a fee, a finalised structure and a class record; a Left student may pay (FR-007). */
@Covers("FR-007")
class ReceiptFeeStateIT extends IntegrationTest {

	static final String TODAY = "3611-10-01";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	Setup setup;

	SignedInClient client;

	@BeforeAll
	void seed() {
		setup = FeeTestData.year(jdbc, "StateIT", LocalDate.of(3611, 4, 1), 1);
		FeeTestData.setStatus(jdbc, setup.yearId(), true);
	}

	@BeforeEach
	void today() throws Exception {
		clock.setDate(LocalDate.of(3611, 10, 1));
		client = SignedInClient.of(mvc);
	}

	private int rows(long studentId) {
		return jdbc.queryForObject("select count(*) from receipts where student_id = ?", Integer.class, studentId);
	}

	@Test
	void feeNotSetIsRefusedWithAClearMessage() throws Exception {
		Fx s = FeeTestData.studentWithAccount(jdbc, setup, 931001, "1000.00", null);
		jdbc.update("update fee_accounts set amount = null where enrollment_id = ?", s.enrollmentId());

		ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("FS-1", TODAY, "10.00"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("FEE_NOT_SET"))
			.andExpect(jsonPath("$.detail").value("Fee not set for this student's standard and category."));
		assertThat(rows(s.studentId())).isZero();
	}

	@Test
	void aClassRecordWithoutAnAccountMeansTheStructureIsNotFinalised() throws Exception {
		long enrollment = FeeTestData.student(jdbc, setup, 931002, 0, 0);
		long student = jdbc.queryForObject("select student_id from student_enrollments where id = ?", Long.class,
				enrollment);

		ReceiptApi.create(client, student, ReceiptApi.cash("FS-2", TODAY, "10.00"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("FEE_NOT_FINALISED"));
		assertThat(rows(student)).isZero();
	}

	@Test
	void noClassRecordInTheCurrentYearIsRefused() throws Exception {
		Setup other = FeeTestData.year(jdbc, "StateOther", LocalDate.of(3613, 4, 1), 1);
		long enrollment = FeeTestData.student(jdbc, other, 931003, 0, 0);
		long student = jdbc.queryForObject("select student_id from student_enrollments where id = ?", Long.class,
				enrollment);

		ReceiptApi.create(client, student, ReceiptApi.cash("FS-3", TODAY, "10.00"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("NO_CLASS_RECORD"));
		assertThat(rows(student)).isZero();
	}

	@Test
	void noCurrentYearIsRefused() throws Exception {
		Fx s = FeeTestData.studentWithAccount(jdbc, setup, 931004, "1000.00", null);
		clock.setDate(LocalDate.of(2001, 1, 1));
		String token = ReceiptApi.token(client, s.studentId());

		ReceiptApi.createWithToken(client, s.studentId(), token, ReceiptApi.cash("FS-4", "2001-01-01", "10.00"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("NO_CURRENT_YEAR"));
		assertThat(rows(s.studentId())).isZero();
	}

	@Test
	void aLeftStudentCanStillPay() throws Exception {
		Fx s = FeeTestData.studentWithAccount(jdbc, setup, 931005, "1000.00", null);
		jdbc.update("update students set status = 'Left' where id = ?", s.studentId());

		ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("FS-5", TODAY, "10.00")).andExpect(status().isCreated());
		assertThat(rows(s.studentId())).isEqualTo(1);
	}

	@Test
	void aStandardWithNoRegistrationFeeTakesNoRegistrationAmount() throws Exception {
		Fx s = FeeTestData.studentWithAccount(jdbc, setup, 931006, "1000.00", null);

		ReceiptApi.create(client, s.studentId(),
				"\"receiptNo\":\"FS-6\",\"receiptDate\":\"" + TODAY + "\",\"registrationPaid\":10.00,\"feePaid\":10.00,"
						+ "\"paymentMode\":\"CASH\"")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("registrationPaid"));
		ReceiptApi.create(client, s.studentId(),
				"\"receiptNo\":\"FS-7\",\"receiptDate\":\"" + TODAY + "\",\"registrationPaid\":0.00,\"feePaid\":10.00,"
						+ "\"paymentMode\":\"CASH\"")
			.andExpect(status().isCreated());
		client.get("/api/students/" + s.studentId() + "/fee-collection")
			.andExpect(jsonPath("$.chargesRegistrationFee").value(false));
	}

	@Test
	void anUnknownStudentIsNotFound() throws Exception {
		client.post("/api/receipts", "{\"studentId\":-3,\"receiptType\":\"CURRENT_YEAR\",\"balanceVersion\":\"x\","
				+ ReceiptApi.cash("FS-8", TODAY, "10") + "}").andExpect(status().isNotFound());
	}

}
