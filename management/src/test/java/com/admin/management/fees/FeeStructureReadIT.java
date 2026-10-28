package com.admin.management.fees;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
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

/** The read side of the fee structure screen: status, rows with names, the class record count and the row form choices. */
@Covers("FR-001")
@Covers("FR-002")
@Covers("FR-013")
class FeeStructureReadIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void aYearWithNoRowsIsADraftWithNoVersionAndNoRows() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "ReadEmpty", LocalDate.of(3001, 4, 1), 1);

		SignedInClient.of(mvc).get("/api/fee-structures/" + setup.yearId())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.yearId").value(setup.yearId()))
			.andExpect(jsonPath("$.yearName").value("ReadEmpty 3001-3002"))
			.andExpect(jsonPath("$.status").value("Draft"))
			.andExpect(jsonPath("$.version").doesNotExist())
			.andExpect(jsonPath("$.studentCount").value(0))
			.andExpect(jsonPath("$.rows", hasSize(0)));
	}

	@Test
	void rowsCarryTheNamesAndTwoDecimalAmounts() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "ReadRows", LocalDate.of(3002, 4, 1), 2);
		FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(0), null, "28000", "500", LocalDate.of(3002, 6, 30));
		FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(1), setup.category(0), "30000.5", null, null);
		FeeTestData.setStatus(jdbc, setup.yearId(), true);

		SignedInClient.of(mvc).get("/api/fee-structures/" + setup.yearId())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("Finalised"))
			.andExpect(jsonPath("$.version").value(0))
			.andExpect(jsonPath("$.rows", hasSize(2)))
			.andExpect(jsonPath("$.rows[0].standardName").value("ReadRows Std 1"))
			.andExpect(jsonPath("$.rows[0].categoryId").doesNotExist())
			.andExpect(jsonPath("$.rows[0].categoryName").value("All categories"))
			.andExpect(jsonPath("$.rows[0].dueDate").value("3002-06-30"))
			.andExpect(jsonPath("$.rows[1].standardName").value("ReadRows Std 2"))
			.andExpect(jsonPath("$.rows[1].categoryId").value(setup.category(0)))
			.andExpect(content().string(containsString("\"amount\":28000.00")))
			.andExpect(content().string(containsString("\"registrationFee\":500.00")))
			.andExpect(content().string(containsString("\"amount\":30000.50")));
	}

	@Test
	void theStudentCountIsEveryClassRecordOfTheYearIncludingLeftStudents() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "ReadCount", LocalDate.of(3003, 4, 1), 1);
		FeeTestData.student(jdbc, setup, 1, 0, 0);
		long left = FeeTestData.student(jdbc, setup, 2, 0, 1);
		jdbc.update("update students set status = 'Left' where id = (select student_id from student_enrollments where id = ?)",
				left);
		Setup other = FeeTestData.year(jdbc, "ReadCountOther", LocalDate.of(3004, 4, 1), 1);
		FeeTestData.student(jdbc, other, 3, 0, 0);

		SignedInClient.of(mvc).get("/api/fee-structures/" + setup.yearId())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.studentCount").value(2));
	}

	@Test
	void anUnknownYearIsNotFound() throws Exception {
		SignedInClient client = SignedInClient.of(mvc);
		client.get("/api/fee-structures/999999999").andExpect(status().isNotFound());
		client.get("/api/fee-structures/999999999/choices").andExpect(status().isNotFound());
	}

	@Test
	void choicesOfferActiveStandardsAndCategoriesAndKeepInactiveOnesARowHolds() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "ReadChoices", LocalDate.of(3005, 4, 1), 3);
		FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(0), setup.category(1), "1000", null, null);
		jdbc.update("update standards set active = false where id in (?, ?)", setup.standard(0), setup.standard(1));
		jdbc.update("update student_categories set active = false where id in (?, ?)", setup.category(1),
				setup.category(2));

		SignedInClient.of(mvc).get("/api/fee-structures/" + setup.yearId() + "/choices")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.standards[*].id", hasItem((int) setup.standard(0))))
			.andExpect(jsonPath("$.standards[*].id", not(hasItem((int) setup.standard(1)))))
			.andExpect(jsonPath("$.standards[*].id", hasItem((int) setup.standard(2))))
			.andExpect(jsonPath("$.categories[*].id", hasItem((int) setup.category(1))))
			.andExpect(jsonPath("$.categories[*].id", not(hasItem((int) setup.category(2)))))
			.andExpect(jsonPath("$.categories[*].id", hasItem((int) setup.category(0))))
			.andExpect(jsonPath("$.standards[?(@.id == " + setup.standard(0) + ")].active", hasItem(false)))
			.andExpect(jsonPath("$.standards[?(@.id == " + setup.standard(2) + ")].active", hasItem(true)))
			.andExpect(jsonPath("$.categories[?(@.id == " + setup.category(1) + ")].active", hasItem(false)))
			.andExpect(jsonPath("$.categories[?(@.id == " + setup.category(0) + ")].active", hasItem(true)));
	}

}
