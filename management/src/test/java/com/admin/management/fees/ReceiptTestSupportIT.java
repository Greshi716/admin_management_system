package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.FeeTestData.Fx;
import com.admin.management.support.FeeTestData.Setup;
import com.admin.management.support.IntegrationTest;

/**
 * The receipt test data builders, the independent Java balance, and the test-profile endpoints that seed receipts,
 * set a brought-forward amount and empty the receipt tables for the browser tests (FR-024).
 */
@Covers("FR-024")
class ReceiptTestSupportIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	Setup setup;

	@BeforeAll
	void seed() {
		setup = FeeTestData.year(jdbc, "RTS", LocalDate.of(2060, 4, 1), 2);
		FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(0), null, "10000.00", "500.00", null);
		FeeTestData.setStatus(jdbc, setup.yearId(), true);
	}

	@Test
	void theBuildersMakeReceiptsAndMarksAndTheJavaBalanceCountsOnlyValidOnes() {
		Fx student = FeeTestData.studentWithAccount(jdbc, setup, 910001, "10000.00", "500.00");
		FeeTestData.cashReceipt(jdbc, setup, student, "RTS-1", LocalDate.of(2060, 5, 1), "500.00", "3000.00");
		long cheque = FeeTestData.chequeReceipt(jdbc, setup, student, "RTS-2", LocalDate.of(2060, 5, 2), "2000.00");
		FeeTestData.cashReceipt(jdbc, setup, student, "RTS-3", LocalDate.of(2060, 5, 3), "0.00", "1000.00");

		FeeTestData.Balance before = FeeTestData.balance(jdbc, student.enrollmentId());
		assertThat(before.paidYearFee()).isEqualByComparingTo("6000.00");
		assertThat(before.paidRegistration()).isEqualByComparingTo("500.00");
		assertThat(before.pendingYearFee()).isEqualByComparingTo("4000.00");
		assertThat(before.pendingRegistration()).isEqualByComparingTo("0.00");

		FeeTestData.markNotCleared(jdbc, cheque, "Bounced");

		FeeTestData.Balance after = FeeTestData.balance(jdbc, student.enrollmentId());
		assertThat(after.paidYearFee()).isEqualByComparingTo("4000.00");
		assertThat(after.pendingYearFee()).isEqualByComparingTo("6000.00");
		// the database views say the same as the Java sums
		assertThat(jdbc.queryForObject("select pending_year_fee from v_fee_position where enrollment_id = ?",
				BigDecimal.class, student.enrollmentId())).isEqualByComparingTo(after.pendingYearFee());
	}

	@Test
	void theBulkGeneratorMakes2000StudentsAnd10000ReceiptsSpreadOverThem() {
		FeeTestData.bulkStudentsAndReceipts(jdbc, setup, "RBULK", 2000, 10000);

		assertThat(count("select count(*) from students where gr_no like 'RBULK-%'")).isEqualTo(2000);
		assertThat(count("select count(*) from fee_accounts a join students s on s.gr_no like 'RBULK-%' "
				+ "join student_enrollments e on e.student_id = s.id and e.id = a.enrollment_id")).isEqualTo(2000);
		assertThat(count("select count(*) from receipts where receipt_no like 'RBULK-R%'")).isEqualTo(10000);
		assertThat(count("select count(distinct student_id) from receipts where receipt_no like 'RBULK-R%'"))
			.isEqualTo(2000);
		assertThat(jdbc.queryForObject("select sum(total) from receipts where receipt_no like 'RBULK-R%'",
				BigDecimal.class)).isEqualByComparingTo("10000.00");
	}

	@Test
	void theTestEndpointsSeedReceiptsSetBroughtForwardAndClearTheReceiptTables() throws Exception {
		Fx student = FeeTestData.studentWithAccount(jdbc, setup, 910002, "10000.00", null);

		mvc.perform(post("/api/test-support/seed-receipts").with(csrf())
			.param("academicYearId", String.valueOf(setup.yearId()))
			.param("count", "5")
			.param("receiptDate", "2060-05-01")
			.param("prefix", "RSEED")
			.param("feePaid", "10.00")).andExpect(status().isOk()).andExpect(jsonPath("$.created").value(5));
		assertThat(count("select count(*) from receipts where receipt_no like 'RSEED-%'")).isEqualTo(5);

		mvc.perform(post("/api/test-support/students/" + student.studentId() + "/brought-forward").with(csrf())
			.param("amount", "2500.00")).andExpect(status().isNoContent());
		mvc.perform(post("/api/test-support/students/" + student.studentId() + "/brought-forward").with(csrf())
			.param("amount", "1800.00")).andExpect(status().isNoContent());
		assertThat(jdbc.queryForObject("select amount from fee_brought_forward where student_id = ?", BigDecimal.class,
				student.studentId())).isEqualByComparingTo("1800.00");

		mvc.perform(post("/api/test-support/seed-receipts").with(csrf())
			.param("academicYearId", String.valueOf(setup.yearId()))
			.param("count", "0")
			.param("receiptDate", "2060-05-01")).andExpect(status().isUnprocessableEntity());

		FeeTestData.markNotCleared(jdbc, FeeTestData.chequeReceipt(jdbc, setup, student, "RTS-CLR", LocalDate.of(2060, 5, 4),
				"10.00"), "Bounced");
		mvc.perform(post("/api/test-support/clear-receipts").with(csrf())).andExpect(status().isNoContent());
		assertThat(count("select count(*) from receipts")).isZero();
		assertThat(count("select count(*) from receipt_marks")).isZero();
		// the triggers are back on afterwards
		long serial = FeeTestData.cashReceipt(jdbc, setup, student, "RTS-AFTER", LocalDate.of(2060, 5, 5), "0.00", "1.00");
		org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataAccessException.class,
				() -> jdbc.update("delete from receipts where serial_no = ?", serial));
	}

	private int count(String sql) {
		return jdbc.queryForObject(sql, Integer.class);
	}

}
