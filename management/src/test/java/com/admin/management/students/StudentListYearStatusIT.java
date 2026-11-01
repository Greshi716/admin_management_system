package com.admin.management.students;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.admin.management.shared.table.TableRequest;
import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.FeeTestData.Fx;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignedInClient;
import com.admin.management.support.YearChangeTestData;
import com.admin.management.support.YearChangeTestData.TwoYears;

/**
 * Tests for year status, last year result and notPromotedYet columns on the Students list (FR-013, FR-015, FR-016).
 */
@Covers("FR-013")
@Covers("FR-015")
@Covers("FR-016")
class StudentListYearStatusIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	SignedInClient client;

	@BeforeEach
	void setUp() throws Exception {
		client = SignedInClient.of(mvc);
	}

	@Test
	void stayersAndPromotedStudentsSeparateWhenFilteredOrSortedByLastYearResult() throws Exception {
		TwoYears years = YearChangeTestData.twoConsecutiveYears(jdbc, "ListStat", LocalDate.of(3931, 4, 1), 2);
		YearChangeTestData.finaliseNewYear(jdbc, years.newYear(), "15000.00");
		YearChangeTestData.startNewYear(clock, years.newYear());

		// Student 1: Promoted from old year to Std 2 in new year
		Fx s1 = FeeTestData.studentWithAccount(jdbc, years.oldYear(), 393101, "12000.00", null);
		jdbc.update("update student_enrollments set year_status = 'Promoted' where id = ?", s1.enrollmentId());
		jdbc.update("""
				insert into student_enrollments (student_id, academic_year_id, standard_id, division_id, category_id, year_start_date, year_status)
				values (?, ?, ?, ?, ?, ?, 'Studying')
				""", s1.studentId(), years.newYear().yearId(), years.newYear().standard(1),
				years.newYear().divisionByStandard().get(years.newYear().standard(1)), years.newYear().category(0), years.newYear().yearStart());

		// Student 2: Detained in old year, stays in Std 1 in new year
		Fx s2 = FeeTestData.studentWithAccount(jdbc, years.oldYear(), 393102, "12000.00", null);
		jdbc.update("update student_enrollments set year_status = 'Detained' where id = ?", s2.enrollmentId());
		jdbc.update("""
				insert into student_enrollments (student_id, academic_year_id, standard_id, division_id, category_id, year_start_date, year_status)
				values (?, ?, ?, ?, ?, ?, 'Studying')
				""", s2.studentId(), years.newYear().yearId(), years.newYear().standard(0),
				years.newYear().divisionByStandard().get(years.newYear().standard(0)), years.newYear().category(0), years.newYear().yearStart());

		// Student 3: Not promoted yet (Studying in old year, no class record in new year)
		Fx s3 = FeeTestData.studentWithAccount(jdbc, years.oldYear(), 393103, "12000.00", null);

		// Filter by lastYearResult = 'Detained'
		String filterDetained = """
				{"first":0,"rows":10,"filters":{"lastYearResult":{"value":"Detained","matchMode":"equals"}}}
				""";
		client.post("/api/students/search", filterDetained)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.rows[0].grNo").value("GR-393102"))
				.andExpect(jsonPath("$.rows[0].lastYearResult").value("Detained"))
				.andExpect(jsonPath("$.rows[0].yearStatus").value("Studying"))
				.andExpect(jsonPath("$.rows[0].notPromotedYet").value(false));

		// Filter by lastYearResult = 'Promoted'
		String filterPromoted = """
				{"first":0,"rows":10,"filters":{"lastYearResult":{"value":"Promoted","matchMode":"equals"}}}
				""";
		client.post("/api/students/search", filterPromoted)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.rows[0].grNo").value("GR-393101"))
				.andExpect(jsonPath("$.rows[0].lastYearResult").value("Promoted"))
				.andExpect(jsonPath("$.rows[0].yearStatus").value("Studying"))
				.andExpect(jsonPath("$.rows[0].notPromotedYet").value(false));

		// Filter by notPromotedYet = true
		String filterNotPromoted = """
				{"first":0,"rows":10,"filters":{"notPromotedYet":{"value":true,"matchMode":"equals"}}}
				""";
		client.post("/api/students/search", filterNotPromoted)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.rows[0].grNo").value("GR-393103"))
				.andExpect(jsonPath("$.rows[0].notPromotedYet").value(true))
				.andExpect(jsonPath("$.rows[0].yearStatus").value("Studying"));
	}

	@Test
	void profileDtoCarriesStream() throws Exception {
		TwoYears years = YearChangeTestData.twoConsecutiveYears(jdbc, "ProfStream", LocalDate.of(3933, 4, 1), 2);
		YearChangeTestData.finaliseNewYear(jdbc, years.newYear(), "15000.00");
		YearChangeTestData.startNewYear(clock, years.newYear());

		Fx student = FeeTestData.studentWithAccount(jdbc, years.newYear(), 393301, "15000.00", null);
		jdbc.update("update student_enrollments set stream = 'Science' where id = ?", student.enrollmentId());

		client.get("/api/students/" + student.studentId())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.stream").value("Science"))
				.andExpect(jsonPath("$.classHistory[0].stream").value("Science"));
	}

}
