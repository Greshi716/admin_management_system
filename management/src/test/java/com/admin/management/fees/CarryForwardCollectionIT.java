package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

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
import com.admin.management.support.JsonMoney;
import com.admin.management.support.ReceiptApi;
import com.admin.management.support.SignedInClient;

/**
 * Integration tests for fee collection after promotion (FR-015, FR-022, FR-023, FR-026).
 */
@Covers("FR-015")
@Covers("FR-022")
@Covers("FR-023")
@Covers("FR-026")
class CarryForwardCollectionIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	SignedInClient client;

	int studentSeq = 0;

	@BeforeEach
	void setUp() throws Exception {
		client = SignedInClient.of(mvc);
	}

	private Setup createYear(String prefix, int yearNumber) {
		Setup s = FeeTestData.year(jdbc, prefix, LocalDate.of(yearNumber, 4, 1), 1);
		FeeTestData.setStatus(jdbc, s.yearId(), true);
		return s;
	}

	@Test
	void paymentOrderWithCarriedAmount() throws Exception {
		Setup year1 = createYear("CFCYear1", 3801);
		Setup year2 = createYear("CFCYear2", 3802);
		clock.setDate(LocalDate.of(3802, 5, 1));

		Fx student = FeeTestData.studentWithAccount(jdbc, year2, 380101 + (++studentSeq), "15000.00", null);
		jdbc.update("insert into fee_brought_forward (student_id, amount, updated_at) values (?, 3000.00, now())",
				student.studentId());

		// 1. Fee collection shows earlier years pending and cannot pay current year yet
		client.get("/api/students/" + student.studentId() + "/fee-collection")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.canPayPreviousYears").value(true))
			.andExpect(jsonPath("$.canPayCurrentYear").value(false))
			.andExpect(JsonMoney.at("$.balance.broughtForward", "3000.00"))
			.andExpect(JsonMoney.at("$.balance.earlierYearsPending", "3000.00"))
			.andExpect(jsonPath("$.broughtForwardMarker").value("brought forward"));

		// 2. Profile fee DTO marks brought forward as "brought forward"
		client.get("/api/students/" + student.studentId() + "/fees")
			.andExpect(status().isOk())
			.andExpect(JsonMoney.at("$.broughtForward.amount", "3000.00"))
			.andExpect(jsonPath("$.broughtForward.marker").value("brought forward"));

		// 3. Current year receipt is refused while earlier years are pending
		ReceiptApi.create(client, student.studentId(), ReceiptApi.cash("CFC-R1", "3802-05-01", "1000.00"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("BROUGHT_FORWARD_PENDING"));

		// 4. Pay previous years pending fee in full
		String token = ReceiptApi.token(client, student.studentId());
		String previousPayload = "{\"studentId\":" + student.studentId() + ",\"balanceVersion\":\"" + token
				+ "\",\"receiptType\":\"PREVIOUS_YEARS\",\"previousYearId\":" + year1.yearId()
				+ ",\"receiptNo\":\"CFC-PY1\",\"receiptDate\":\"3802-05-01\",\"feePaid\":3000.00,\"paymentMode\":\"CASH\"}";
		client.post("/api/receipts", previousPayload).andExpect(status().isCreated());

		// 5. Now earlier years pending is cleared, current year can be paid
		client.get("/api/students/" + student.studentId() + "/fee-collection")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.canPayPreviousYears").value(false))
			.andExpect(jsonPath("$.canPayCurrentYear").value(true))
			.andExpect(JsonMoney.at("$.balance.earlierYearsPending", "0.00"));

		ReceiptApi.create(client, student.studentId(), ReceiptApi.cash("CFC-R2", "3802-05-01", "5000.00"))
			.andExpect(status().isCreated());
	}

	@Test
	void carriedAmountUnchangedByLaterOldYearOrFeeStructureChanges() throws Exception {
		Setup year1 = createYear("CFCFix1", 3803);
		Setup year2 = createYear("CFCFix2", 3804);
		clock.setDate(LocalDate.of(3804, 5, 1));

		Fx student = FeeTestData.studentWithAccount(jdbc, year2, 380101 + (++studentSeq), "10000.00", null);
		jdbc.update("insert into fee_brought_forward (student_id, amount, updated_at) values (?, 3000.00, now())",
				student.studentId());

		// Old year structure change or row insertion must not recalculate fee_brought_forward
		jdbc.update("update fee_rows set amount = 20000.00 where academic_year_id = ?", year1.yearId());

		BigDecimal savedAmount = jdbc.queryForObject("select amount from fee_brought_forward where student_id = ?",
				BigDecimal.class, student.studentId());
		assertThat(savedAmount.compareTo(new BigDecimal("3000.00"))).isZero();

		client.get("/api/students/" + student.studentId() + "/fee-collection")
			.andExpect(status().isOk())
			.andExpect(JsonMoney.at("$.balance.broughtForward", "3000.00"))
			.andExpect(JsonMoney.at("$.balance.earlierYearsPending", "3000.00"));

		client.get("/api/students/" + student.studentId() + "/fees")
			.andExpect(status().isOk())
			.andExpect(JsonMoney.at("$.broughtForward.amount", "3000.00"));
	}

	@Test
	void notPromotedMessageAndNoReceiptForUnpromotedStudent() throws Exception {
		Setup year1 = createYear("CFCUnp1", 3805);
		Setup year2 = createYear("CFCUnp2", 3806);
		clock.setDate(LocalDate.of(3806, 5, 1));

		// Student has an enrollment in year1, but not year2
		Fx student = FeeTestData.studentWithAccount(jdbc, year1, 380101 + (++studentSeq), "12000.00", null);

		client.get("/api/students/" + student.studentId() + "/fee-collection")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.currentYear.state").value("NO_CLASS_RECORD"))
			.andExpect(jsonPath("$.canPayCurrentYear").value(false))
			.andExpect(jsonPath("$.message").value("Not promoted yet: promote the student first"))
			.andExpect(jsonPath("$.currentYear.message").value("Not promoted yet: promote the student first"));

		// Taking a current-year receipt is refused
		ReceiptApi.create(client, student.studentId(), ReceiptApi.cash("CFC-R3", "3806-05-01", "1000.00"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("NO_CLASS_RECORD"));
	}

	@Test
	void leftStudentWithPendingFeeIsStillListed() throws Exception {
		Setup year1 = createYear("CFCLeft1", 3807);
		Setup year2 = createYear("CFCLeft2", 3808);
		clock.setDate(LocalDate.of(3808, 5, 1));

		Fx student = FeeTestData.studentWithAccount(jdbc, year1, 380101 + (++studentSeq), "12000.00", null);
		// Mark student as Left
		jdbc.update("update students set status = 'Left' where id = ?", student.studentId());
		jdbc.update("update student_enrollments set year_status = 'Left' where id = ?", student.enrollmentId());
		jdbc.update("insert into fee_brought_forward (student_id, amount, updated_at) values (?, 2500.00, now())",
				student.studentId());

		client.get("/api/students/" + student.studentId() + "/fees")
			.andExpect(status().isOk())
			.andExpect(JsonMoney.at("$.broughtForward.amount", "2500.00"));

		client.get("/api/students/" + student.studentId() + "/fee-collection")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.student.status").value("Left"))
			.andExpect(jsonPath("$.canPayPreviousYears").value(true))
			.andExpect(JsonMoney.at("$.balance.earlierYearsPending", "2500.00"));
	}

}
