package com.admin.management.yearchange;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.YearChangeTestData;
import com.admin.management.support.YearChangeTestData.TwoYears;

/**
 * Integration tests for year change promotion status endpoint (FR-006, FR-014, FR-015).
 */
@Covers("FR-006")
@Covers("FR-014")
@Covers("FR-015")
class PromotionStatusIT extends IntegrationTest {

	@Autowired
	PromotionStatusService statusService;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void statusActiveWhenPendingStudentsExist() {
		TwoYears years = YearChangeTestData.twoConsecutiveYears(jdbc, "StatAct", LocalDate.of(3801, 4, 1), 2);
		YearChangeTestData.startNewYear(clock, years.newYear());

		// Seed a student studying in the old year
		FeeTestData.student(jdbc, years.oldYear(), 380101, 0, 0);

		YearChangeStatusDto status = statusService.getStatus();
		assertThat(status.active()).isTrue();
		assertThat(status.newYear()).contains("3802-3803");
		assertThat(status.oldYear()).contains("3801-3802");
		assertThat(status.standards()).isNotEmpty();
		assertThat(status.standards().get(0).standardId()).isEqualTo(years.oldYear().standard(0));
		assertThat(status.standards().get(0).pending()).isGreaterThanOrEqualTo(1);
	}

	@Test
	void statusInactiveWhenAllStudentsPromoted() {
		TwoYears years = YearChangeTestData.twoConsecutiveYears(jdbc, "StatInact", LocalDate.of(3803, 4, 1), 2);
		YearChangeTestData.startNewYear(clock, years.newYear());

		long enrId = FeeTestData.student(jdbc, years.oldYear(), 380301, 0, 0);
		jdbc.update("update student_enrollments set year_status = 'Promoted' where id = ?", enrId);

		YearChangeStatusDto status = statusService.getStatus();
		assertThat(status.active()).isFalse();
		assertThat(status.standards()).isEmpty();
	}

}
