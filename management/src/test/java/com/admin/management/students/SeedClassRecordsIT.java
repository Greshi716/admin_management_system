package com.admin.management.students;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** The test-profile endpoint that seeds class records in bulk for the browser tests and the quickstart (scenarios 19 and 22). */
@Covers("FR-006")
@Covers("FR-014")
class SeedClassRecordsIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void seedsStudentsWithOneClassRecordEachSpreadOverTheCategories() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "Seed", LocalDate.of(2080, 4, 1), 1);
		long standard = setup.standard(0);
		long division = setup.divisionByStandard().get(standard);

		mvc.perform(post("/api/test-support/seed-class-records").with(csrf())
			.param("academicYearId", String.valueOf(setup.yearId()))
			.param("standardId", String.valueOf(standard))
			.param("divisionId", String.valueOf(division))
			.param("count", "70")
			.param("grPrefix", "SEEDA"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.created").value(70));

		assertThat(count("select count(*) from student_enrollments where academic_year_id = ? and standard_id = ? "
				+ "and division_id = ? and year_start_date = date '2080-04-01'", setup.yearId(), standard, division))
			.isEqualTo(70);
		assertThat(count("select count(*) from students where gr_no like 'SEEDA-%' and status = 'Active' "
				+ "and admission_standard_id = ?", standard)).isEqualTo(70);
		assertThat(count("select count(*) from student_gr_numbers where gr_key like 'seeda-%'")).isEqualTo(70);
		assertThat(count("select count(distinct category_id) from student_enrollments where academic_year_id = ?",
				setup.yearId())).isEqualTo(7);
		assertThat(count("select count(*) from student_enrollments e join students s on s.id = e.student_id "
				+ "where e.academic_year_id = ? and e.category_id = s.category_id", setup.yearId())).isEqualTo(70);
	}

	@Test
	void anOptionalCategoryPutsEveryStudentInThatCategory() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "SeedCat", LocalDate.of(2081, 4, 1), 1);
		long standard = setup.standard(0);

		mvc.perform(post("/api/test-support/seed-class-records").with(csrf())
			.param("academicYearId", String.valueOf(setup.yearId()))
			.param("standardId", String.valueOf(standard))
			.param("divisionId", String.valueOf(setup.divisionByStandard().get(standard)))
			.param("categoryId", String.valueOf(setup.category(2)))
			.param("count", "5")
			.param("grPrefix", "SEEDB"))
			.andExpect(status().isOk());

		assertThat(count("select count(distinct category_id) from student_enrollments where academic_year_id = ?",
				setup.yearId())).isEqualTo(1);
		assertThat(count("select count(*) from student_enrollments where academic_year_id = ? and category_id = ?",
				setup.yearId(), setup.category(2))).isEqualTo(5);
	}

	@Test
	void aDivisionOfAnotherYearOrStandardIsRefusedAndNothingIsSeeded() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "SeedBad", LocalDate.of(2082, 4, 1), 2);
		Setup other = FeeTestData.year(jdbc, "SeedOther", LocalDate.of(2083, 4, 1), 1);
		long standard = setup.standard(0);

		mvc.perform(post("/api/test-support/seed-class-records").with(csrf())
			.param("academicYearId", String.valueOf(setup.yearId()))
			.param("standardId", String.valueOf(standard))
			.param("divisionId", String.valueOf(setup.divisionByStandard().get(setup.standard(1))))
			.param("count", "3")
			.param("grPrefix", "SEEDC"))
			.andExpect(status().isUnprocessableEntity());
		mvc.perform(post("/api/test-support/seed-class-records").with(csrf())
			.param("academicYearId", String.valueOf(setup.yearId()))
			.param("standardId", String.valueOf(standard))
			.param("divisionId", String.valueOf(other.divisionByStandard().get(other.standard(0))))
			.param("count", "3")
			.param("grPrefix", "SEEDC"))
			.andExpect(status().isUnprocessableEntity());
		mvc.perform(post("/api/test-support/seed-class-records").with(csrf())
			.param("academicYearId", String.valueOf(setup.yearId()))
			.param("standardId", String.valueOf(standard))
			.param("divisionId", String.valueOf(setup.divisionByStandard().get(standard)))
			.param("count", "0")
			.param("grPrefix", "SEEDC"))
			.andExpect(status().isUnprocessableEntity());

		assertThat(count("select count(*) from students where gr_no like 'SEEDC-%'")).isZero();
	}

	@Test
	void aGrNoThatIsAlreadyUsedIsRefusedWithoutAPartialSeed() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "SeedDup", LocalDate.of(2084, 4, 1), 1);
		long standard = setup.standard(0);
		var request = post("/api/test-support/seed-class-records").with(csrf())
			.param("academicYearId", String.valueOf(setup.yearId()))
			.param("standardId", String.valueOf(standard))
			.param("divisionId", String.valueOf(setup.divisionByStandard().get(standard)))
			.param("count", "4")
			.param("grPrefix", "SEEDD");
		mvc.perform(request).andExpect(status().isOk());

		mvc.perform(request).andExpect(status().isConflict());

		assertThat(count("select count(*) from students where gr_no like 'SEEDD-%'")).isEqualTo(4);
	}

	private int count(String sql, Object... args) {
		return jdbc.queryForObject(sql, Integer.class, args);
	}

}
