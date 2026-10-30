package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.admin.management.support.JsonMoney;
import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.FeeTestData.Fx;
import com.admin.management.support.FeeTestData.Setup;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.ReceiptApi;
import com.admin.management.support.SignedInClient;

/** Saving a current-year receipt: the numbers, the date, the amounts, the mode and what is stored (quickstart 1 to 5, 19). */
@Covers("FR-001")
@Covers("FR-002")
@Covers("FR-003")
@Covers("FR-006")
@Covers("FR-010")
class ReceiptCreateIT extends IntegrationTest {

	static final String TODAY = "3601-10-01";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	Setup setup;

	SignedInClient client;

	int students;

	@BeforeAll
	void seed() {
		setup = FeeTestData.year(jdbc, "CreateIT", LocalDate.of(3601, 4, 1), 1);
		FeeTestData.setStatus(jdbc, setup.yearId(), true);
	}

	@BeforeEach
	void today() throws Exception {
		clock.setDate(LocalDate.of(3601, 10, 1));
		client = SignedInClient.of(mvc);
	}

	Fx student(String fee, String registration) {
		return FeeTestData.studentWithAccount(jdbc, setup, 930000 + (++students), fee, registration);
	}

	@Test
	void aCashReceiptWithBothRowsIsSavedWithItsSerialNoTotalAndWords() throws Exception {
		Fx s = student("36500.00", "500.00");

		var saved = ReceiptApi.create(client, s.studentId(),
				"\"receiptNo\":\"3100\",\"receiptDate\":\"" + TODAY + "\",\"registrationPaid\":500.00,"
						+ "\"feePaid\":10000.00,\"paymentMode\":\"CASH\"");

		saved.andExpect(status().isCreated())
			.andExpect(jsonPath("$.serialNo").isNumber())
			.andExpect(jsonPath("$.receiptNo").value("3100"))
			.andExpect(jsonPath("$.status").value("VALID"))
			.andExpect(jsonPath("$.totalWords").value("Ten Thousand Five Hundred Rupees Only"))
			.andExpect(jsonPath("$.studentId").value(s.studentId()))
			.andExpect(jsonPath("$.grNo").value(s.grNo()))
			.andExpect(jsonPath("$.standardName").value("CreateIT Std 1"))
			.andExpect(jsonPath("$.divisionName").value("A"))
			.andExpect(jsonPath("$.school.name").value("Test School"))
			.andExpect(jsonPath("$.balanceVersion").isString());
		Map<String, Object> row = jdbc.queryForMap("select * from receipts where serial_no = ?",
				ReceiptApi.serial(saved));
		assertThat((BigDecimal) row.get("total")).isEqualByComparingTo("10500.00");
		assertThat((BigDecimal) row.get("registration_paid")).isEqualByComparingTo("500.00");
		assertThat(row.get("payment_mode")).isEqualTo("CASH");
		assertThat(row.get("created_by")).isEqualTo(jdbc.queryForObject("select id from users limit 1", Long.class));
		assertThat(row.get("enrollment_id")).isEqualTo(s.enrollmentId());
		assertThat(row.get("cheque_no")).isNull();
		client.get("/api/receipts/" + ReceiptApi.serial(saved))
			.andExpect(status().isOk())
			.andExpect(JsonMoney.at("$.total", "10500.00"));
		client.get("/api/receipts/-5").andExpect(status().isNotFound());
	}

	@Test
	void aReceiptNumberUsedByAnotherReceiptIsRefusedNamingThatReceipt() throws Exception {
		Fx first = student("1000.00", null);
		Fx second = student("1000.00", null);
		var saved = ReceiptApi.create(client, first.studentId(), ReceiptApi.cash("D-1", TODAY, "100.00"));
		long serial = ReceiptApi.serial(saved);

		ReceiptApi.create(client, second.studentId(), ReceiptApi.cash("D-1", TODAY, "100.00"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DUPLICATE_RECEIPT_NO"))
			.andExpect(jsonPath("$.fieldErrors[0].field").value("receiptNo"))
			.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("serial no. " + serial)))
			.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(TODAY)));
		ReceiptApi.create(client, second.studentId(), ReceiptApi.cash("  d-1 ", TODAY, "100.00"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DUPLICATE_RECEIPT_NO"));
		assertThat(count("select count(*) from receipts where student_id = ?", second.studentId())).isZero();
	}

	@Test
	void numbersAreComparedAsTypedSoALeadingZeroMakesADifferentReceipt() throws Exception {
		Fx s = student("1000.00", null);

		ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("3200", TODAY, "100.00")).andExpect(status().isCreated());
		ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("03200", TODAY, "100.00"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.receiptNo").value("03200"));
		ReceiptApi.create(client, s.studentId(), ReceiptApi.cash(" 3201 ", TODAY, "100.00"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.receiptNo").value("3201"));
	}

	@Test
	void aMissingOrTooLongReceiptNumberIsRefusedBesideTheField() throws Exception {
		Fx s = student("1000.00", null);

		ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("   ", TODAY, "100.00"))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("receiptNo"));
		ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("123456789012345678901", TODAY, "100.00"))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("receiptNo"));
		ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("12345678901234567890", TODAY, "100.00"))
			.andExpect(status().isCreated());
	}

	@Test
	void aFutureDateIsRefusedAndAPastDateEvenBeforeTheYearStartIsAccepted() throws Exception {
		Fx s = student("1000.00", null);

		ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("DT-1", "3601-10-02", "100.00"))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("receiptDate"));
		ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("DT-2", "3601-10-01", "100.00"))
			.andExpect(status().isCreated());
		ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("DT-3", "3601-03-15", "100.00"))
			.andExpect(status().isCreated());
		ReceiptApi.create(client, s.studentId(),
				"\"receiptNo\":\"DT-4\",\"feePaid\":100.00,\"paymentMode\":\"CASH\"")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("receiptDate"));
	}

	@Test
	void bothAmountsZeroIsRefusedButOneRowAboveZeroIsEnough() throws Exception {
		Fx s = student("1000.00", "50.00");

		ReceiptApi.create(client, s.studentId(),
				"\"receiptNo\":\"Z-1\",\"receiptDate\":\"" + TODAY + "\",\"registrationPaid\":0.00,\"feePaid\":0.00,"
						+ "\"paymentMode\":\"CASH\"")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[?(@.field == 'feePaid')]").exists())
			.andExpect(jsonPath("$.fieldErrors[?(@.field == 'registrationPaid')]").exists());
		ReceiptApi.create(client, s.studentId(),
				"\"receiptNo\":\"Z-2\",\"receiptDate\":\"" + TODAY + "\",\"registrationPaid\":50.00,\"paymentMode\":\"CASH\"")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.totalWords").value("Fifty Rupees Only"));
		ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("Z-3", TODAY, "0.01"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.totalWords").value("One Paisa Only"));
	}

	@Test
	void aChequeNeedsItsNumberDateAndBankAndACashReceiptAcceptsNone() throws Exception {
		Fx s = student("1000.00", null);
		String base = "\"receiptNo\":\"CQ-1\",\"receiptDate\":\"" + TODAY + "\",\"feePaid\":100.00,";

		ReceiptApi.create(client, s.studentId(), base + "\"paymentMode\":\"CHEQUE\"")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors.length()").value(3))
			.andExpect(jsonPath("$.fieldErrors[?(@.field == 'chequeNo')]").exists())
			.andExpect(jsonPath("$.fieldErrors[?(@.field == 'bankName')]").exists())
			.andExpect(jsonPath("$.fieldErrors[?(@.field == 'chequeDate')]").exists());
		ReceiptApi.create(client, s.studentId(),
				base + "\"paymentMode\":\"CASH\",\"chequeNo\":\"123456\",\"bankName\":\"B\",\"chequeDate\":\"" + TODAY + "\"")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors.length()").value(3));
		ReceiptApi.create(client, s.studentId(), base + "\"paymentMode\":\"CHEQUE\",\"chequeNo\":\" 123456 \","
				+ "\"bankName\":\"Test Bank\",\"chequeDate\":\"3601-10-05\"")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.paymentMode").value("CHEQUE"))
			.andExpect(jsonPath("$.chequeNo").value("123456"))
			.andExpect(jsonPath("$.bankName").value("Test Bank"))
			.andExpect(jsonPath("$.chequeDate").value("3601-10-05"));
		ReceiptApi.create(client, s.studentId(), base.replace("CQ-1", "CQ-2") + "\"paymentMode\":\"CREDIT\"")
			.andExpect(status().is4xxClientError());
		ReceiptApi.create(client, s.studentId(), "\"receiptNo\":\"CQ-3\",\"receiptDate\":\"" + TODAY + "\",\"feePaid\":10")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("paymentMode"));
	}

	@Test
	void anUnsavedReceiptLeavesNoRowAndTheSerialNoOfASavedOneIsNeverReused() throws Exception {
		Fx s = student("1000.00", null);
		long before = count("select count(*) from receipts");

		ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("S-1", "3601-10-09", "100.00"))
			.andExpect(status().isUnprocessableEntity());
		assertThat(count("select count(*) from receipts")).isEqualTo(before);

		long first = ReceiptApi.serial(ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("S-2", TODAY, "100.00")));
		long second = ReceiptApi.serial(ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("S-3", TODAY, "100.00")));
		assertThat(second).isGreaterThan(first);
	}

	private long count(String sql, Object... args) {
		return jdbc.queryForObject(sql, Long.class, args);
	}

}
