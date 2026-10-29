package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.admin.management.support.StudentTestData;
import com.admin.management.support.StudentTestData.YearSetup;

/** The Fees block of the student profile: the state, the amounts, the brought-forward amount and the total due. */
@Covers("FR-005")
@Covers("FR-009")
@Covers("FR-011")
class StudentFeesIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	private long studentOf(long enrollmentId) {
		return jdbc.queryForObject("select student_id from student_enrollments where id = ?", Long.class, enrollmentId);
	}

	private void finalise(Setup setup) throws Exception {
		SignedInClient.of(mvc).post("/api/fee-structures/" + setup.yearId() + "/finalise", "{\"version\":0}")
			.andExpect(status().isOk());
	}

	private void broughtForward(long studentId, String amount, String note) {
		jdbc.update("insert into fee_brought_forward (student_id, amount, note, updated_at) values (?, ?::numeric, ?, now())",
				studentId, amount, note);
	}

	@Test
	void aFinalisedYearWithAFeeShowsTheFeeTheTotalAndTheBroughtForwardAmount() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "PfSet", LocalDate.of(3401, 4, 1), 1);
		FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(0), null, "28000", "500", LocalDate.of(3401, 6, 30));
		FeeTestData.setStatus(jdbc, setup.yearId(), false);
		long student = studentOf(FeeTestData.student(jdbc, setup, 1, 0, 0));
		broughtForward(student, "1200.50", "Old dues");
		finalise(setup);
		clock.setDate(LocalDate.of(3401, 10, 1));

		SignedInClient.of(mvc).get("/api/students/" + student + "/fees")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("FEE_SET"))
			.andExpect(jsonPath("$.yearId").value(setup.yearId()))
			.andExpect(jsonPath("$.yearName").value("PfSet 3401-3402"))
			.andExpect(content().string(containsString("\"yearFee\":28000.00")))
			.andExpect(content().string(containsString("\"registrationFee\":500.00")))
			.andExpect(content().string(containsString("\"totalFee\":28500.00")))
			.andExpect(content().string(containsString("\"totalDue\":29700.50")))
			.andExpect(jsonPath("$.dueDate").value("3401-06-30"))
			.andExpect(jsonPath("$.broughtForward.note").value("Old dues"))
			.andExpect(jsonPath("$.broughtForward.version").value(0))
			.andExpect(content().string(containsString("\"amount\":1200.50")));
	}

	@Test
	void aStudentWithNoRowForTheirStandardShowsFeeNotSetAndTheBroughtForwardAlone() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "PfNotSet", LocalDate.of(3402, 4, 1), 2);
		FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(0), null, "28000", null, null);
		FeeTestData.setStatus(jdbc, setup.yearId(), false);
		long student = studentOf(FeeTestData.student(jdbc, setup, 2, 1, 0));
		broughtForward(student, "300", null);
		finalise(setup);
		clock.setDate(LocalDate.of(3402, 10, 1));

		SignedInClient.of(mvc).get("/api/students/" + student + "/fees")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("FEE_NOT_SET"))
			.andExpect(jsonPath("$.yearFee").doesNotExist())
			.andExpect(jsonPath("$.totalFee").doesNotExist())
			.andExpect(content().string(containsString("\"totalDue\":300.00")));
	}

	@Test
	void aDraftYearOrAStudentWithNoClassRecordIsNotFinalised() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "PfDraft", LocalDate.of(3403, 4, 1), 1);
		FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(0), null, "28000", null, null);
		FeeTestData.setStatus(jdbc, setup.yearId(), false);
		long student = studentOf(FeeTestData.student(jdbc, setup, 3, 0, 0));
		long without = StudentTestData.insertStudent(jdbc, StudentTestData.valid(4, setup.category(0), setup.standard(0)));
		broughtForward(without, "75.25", null);
		clock.setDate(LocalDate.of(3403, 10, 1));
		SignedInClient client = SignedInClient.of(mvc);

		client.get("/api/students/" + student + "/fees")
			.andExpect(jsonPath("$.state").value("NOT_FINALISED"))
			.andExpect(jsonPath("$.yearFee").doesNotExist())
			.andExpect(content().string(containsString("\"totalDue\":0.00")));
		client.get("/api/students/" + without + "/fees")
			.andExpect(jsonPath("$.state").value("NOT_FINALISED"))
			.andExpect(jsonPath("$.yearId").doesNotExist())
			.andExpect(content().string(containsString("\"totalDue\":75.25")));
	}

	@Test
	void aYearIdShowsAnotherYearsRecordWhileTheProfileDefaultsToTheShownOne() throws Exception {
		Setup older = FeeTestData.year(jdbc, "PfOld", LocalDate.of(3404, 4, 1), 1);
		FeeTestData.addRow(jdbc, older.yearId(), older.standard(0), null, "20000", null, null);
		FeeTestData.setStatus(jdbc, older.yearId(), false);
		long enrollment = FeeTestData.student(jdbc, older, 5, 0, 0);
		long student = studentOf(enrollment);
		finalise(older);
		Setup newer = FeeTestData.year(jdbc, "PfNew", LocalDate.of(3405, 4, 1), 1);
		StudentTestData.insertEnrollment(jdbc, student,
				new YearSetup(newer.yearId(), newer.yearStart(), newer.standard(0), newer.divisionByStandard().get(newer.standard(0))),
				newer.category(0));
		clock.setDate(LocalDate.of(3405, 10, 1));
		SignedInClient client = SignedInClient.of(mvc);

		client.get("/api/students/" + student + "/fees").andExpect(jsonPath("$.state").value("NOT_FINALISED"))
			.andExpect(jsonPath("$.yearId").value(newer.yearId()));
		client.get("/api/students/" + student + "/fees?yearId=" + older.yearId())
			.andExpect(jsonPath("$.state").value("FEE_SET"))
			.andExpect(content().string(containsString("\"yearFee\":20000.00")));
	}

	@Test
	void anUnknownStudentIsNotFoundAndTheAnswerCarriesTheBalanceAndTheReceipts() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "PfShape", LocalDate.of(3406, 4, 1), 1);
		FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(0), null, "1000", null, null);
		FeeTestData.setStatus(jdbc, setup.yearId(), false);
		long student = studentOf(FeeTestData.student(jdbc, setup, 6, 0, 0));
		finalise(setup);
		clock.setDate(LocalDate.of(3406, 10, 1));
		SignedInClient client = SignedInClient.of(mvc);

		client.get("/api/students/999999999/fees").andExpect(status().isNotFound());
		var body = client.get("/api/students/" + student + "/fees").andExpect(status().isOk())
			.andExpect(jsonPath("$.balance.paid").value(0.00))
			.andExpect(jsonPath("$.balance.pending").value(1000.00))
			.andExpect(jsonPath("$.balance.paidUpTo.state").value("NOTHING"))
			.andExpect(jsonPath("$.receipts").isEmpty())
			.andReturn().getResponse().getContentAsString();
		assertThat(body).contains("totalDue");
	}

}
