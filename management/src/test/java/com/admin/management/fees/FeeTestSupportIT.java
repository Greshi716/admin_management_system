package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/**
 * The fee test data builder, and the test-profile endpoint that empties the fee tables so a browser test can clear
 * students and master data afterwards (the foreign keys are RESTRICT).
 */
@Covers("FR-006")
@Covers("FR-014")
class FeeTestSupportIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void theBuilderMakesAYearWithRowsStatusAndSpreadClassRecords() {
		Setup setup = FeeTestData.year(jdbc, "Builder", LocalDate.of(2060, 4, 1), 12);
		FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(9), null, "36000.00", "500.00", LocalDate.of(2060, 6, 30));
		FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(9), setup.category(0), "40000.00", null, null);
		FeeTestData.setStatus(jdbc, setup.yearId(), true);
		long single = FeeTestData.student(jdbc, setup, 900001, 9, 0);

		int made = FeeTestData.seedClassRecords(jdbc, setup, "BULK", 2000);

		assertThat(setup.standardIds()).hasSize(12);
		assertThat(setup.categoryIds()).hasSize(7);
		assertThat(made).isEqualTo(2000);
		assertThat(count("select count(*) from student_enrollments where academic_year_id = ?", setup.yearId()))
			.isEqualTo(2001);
		assertThat(count("select count(distinct standard_id) from student_enrollments where academic_year_id = ?",
				setup.yearId())).isEqualTo(12);
		assertThat(count("select count(distinct category_id) from student_enrollments where academic_year_id = ?",
				setup.yearId())).isEqualTo(7);
		assertThat(count("select count(*) from student_enrollments e join divisions d on d.id = e.division_id "
				+ "where e.academic_year_id = ? and d.standard_id = e.standard_id and d.academic_year_id = e.academic_year_id",
				setup.yearId())).isEqualTo(2001);
		assertThat(count("select count(*) from student_gr_numbers where gr_key like 'bulk-%'")).isEqualTo(2000);
		assertThat(count("select count(*) from fee_rows where academic_year_id = ?", setup.yearId())).isEqualTo(2);
		assertThat(jdbc.queryForObject("select status from fee_year_status where academic_year_id = ?", String.class,
				setup.yearId())).isEqualTo("Finalised");
		assertThat(single).isPositive();
	}

	@Test
	void clearFeesEmptiesTheFeeTablesSoStudentsAndMasterDataCanBeCleared() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "Clear", LocalDate.of(2070, 4, 1), 2);
		FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(0), null, "100.00", null, null);
		FeeTestData.setStatus(jdbc, setup.yearId(), true);
		long enrollment = FeeTestData.student(jdbc, setup, 900101, 0, 0);
		long studentId = jdbc.queryForObject("select student_id from student_enrollments where id = ?", Long.class,
				enrollment);
		jdbc.update("insert into fee_accounts (enrollment_id, academic_year_id, amount, updated_at) values (?, ?, 100, now())",
				enrollment, setup.yearId());
		jdbc.update("insert into fee_brought_forward (student_id, amount, updated_at) values (?, 5, now())", studentId);

		mvc.perform(post("/api/test-support/clear-students").with(csrf()))
			.andExpect(status().isConflict()); // RESTRICT: fee accounts still point at the class record
		mvc.perform(post("/api/test-support/clear-fees").with(csrf())).andExpect(status().isNoContent());

		for (String table : new String[] { "fee_accounts", "fee_brought_forward", "fee_rows", "fee_year_status" }) {
			assertThat(count("select count(*) from " + table)).as(table).isZero();
		}
		mvc.perform(post("/api/test-support/clear-students").with(csrf())).andExpect(status().isNoContent());
		assertThat(count("select count(*) from student_enrollments")).isZero();
		jdbc.update("delete from divisions");
		jdbc.update("delete from academic_years");
		jdbc.update("delete from standards");
	}

	private int count(String sql, Object... args) {
		return jdbc.queryForObject(sql, Integer.class, args);
	}

}
