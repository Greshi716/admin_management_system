package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;
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

/** Test plan FR-008 rows B3, B5, F3 and SC-005: the lock applies per year, to Left students, and as a whole. */
@Covers("FR-008")
class FeeLockCasesIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	private record Fixture(Setup setup, long studentId, long enrollmentId, long divisionB) {
	}

	private Fixture finalisedYear(String prefix, int yearNumber) throws Exception {
		Setup setup = FeeTestData.year(jdbc, prefix, LocalDate.of(yearNumber, 4, 1), 2);
		FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(0), null, "36000", "500", null);
		FeeTestData.setStatus(jdbc, setup.yearId(), false);
		long enrollment = FeeTestData.student(jdbc, setup, yearNumber * 10, 0, 0);
		long studentId = jdbc.queryForObject("select student_id from student_enrollments where id = ?", Long.class,
				enrollment);
		long divisionB = jdbc.queryForObject(
				"insert into divisions (academic_year_id, standard_id, name) values (?, ?, 'B') returning id", Long.class,
				setup.yearId(), setup.standard(0));
		SignedInClient.of(mvc).post("/api/fee-structures/" + setup.yearId() + "/finalise", "{\"version\":0}")
			.andExpect(status().isOk());
		return new Fixture(setup, studentId, enrollment, divisionB);
	}

	private String body(long enrollmentId, long standardId, long divisionId, long categoryId) {
		long version = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class, enrollmentId);
		return "{\"version\":" + version + ",\"standardId\":" + standardId + ",\"divisionId\":" + divisionId
				+ ",\"categoryId\":" + categoryId + "}";
	}

	private String url(long studentId, long yearId) {
		return "/api/students/" + studentId + "/enrollments/" + yearId;
	}

	private long enrollmentAudits(long enrollmentId) {
		return jdbc.queryForObject("select count(*) from audit_log where entity = ?", Long.class,
				"StudentEnrollment#" + enrollmentId);
	}

	@Test
	void theLockOfOneYearDoesNotTouchAnotherYearOfTheSameStudent() throws Exception {
		Fixture f = finalisedYear("LcYear", 3741);
		Setup draft = FeeTestData.year(jdbc, "LcYearDraft", LocalDate.of(3742, 4, 1), 2);
		FeeTestData.addRow(jdbc, draft.yearId(), draft.standard(0), null, "1000", null, null);
		FeeTestData.setStatus(jdbc, draft.yearId(), false);
		StudentTestData.insertEnrollment(jdbc, f.studentId(), new YearSetup(draft.yearId(), draft.yearStart(),
				draft.standard(0), draft.divisionByStandard().get(draft.standard(0))), draft.category(0));
		long draftEnrollment = jdbc.queryForObject(
				"select id from student_enrollments where student_id = ? and academic_year_id = ?", Long.class,
				f.studentId(), draft.yearId());
		SignedInClient client = SignedInClient.of(mvc);

		client.put(url(f.studentId(), draft.yearId()), body(draftEnrollment, draft.standard(1),
				draft.divisionByStandard().get(draft.standard(1)), draft.category(1)))
			.andExpect(status().isOk());
		client.put(url(f.studentId(), f.setup().yearId()), body(f.enrollmentId(), f.setup().standard(1),
				f.setup().divisionByStandard().get(f.setup().standard(1)), f.setup().category(0)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("FEE_STRUCTURE_FINALISED"));
	}

	@Test
	void aLeftStudentIsLockedLikeAnyOther() throws Exception {
		Fixture f = finalisedYear("LcLeft", 3743);
		jdbc.update("update students set status = 'Left' where id = ?", f.studentId());

		SignedInClient.of(mvc).put(url(f.studentId(), f.setup().yearId()), body(f.enrollmentId(), f.setup().standard(0),
				f.setup().divisionByStandard().get(f.setup().standard(0)), f.setup().category(1)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("FEE_STRUCTURE_FINALISED"));

		assertThat(jdbc.queryForObject("select category_id from student_enrollments where id = ?", Long.class,
				f.enrollmentId())).isEqualTo(f.setup().category(0));
	}

	@Test
	void changingStandardAndDivisionTogetherIsRefusedAsAWholeAndLeavesTheDivisionAlone() throws Exception {
		Fixture f = finalisedYear("LcBoth", 3744);
		long otherStandard = f.setup().standard(1);

		SignedInClient.of(mvc).put(url(f.studentId(), f.setup().yearId()), body(f.enrollmentId(), otherStandard,
				f.setup().divisionByStandard().get(otherStandard), f.setup().category(0)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("FEE_STRUCTURE_FINALISED"))
			.andExpect(jsonPath("$.fieldErrors[0].field").value("standardId"));

		assertThat(jdbc.queryForObject("select standard_id from student_enrollments where id = ?", Long.class,
				f.enrollmentId())).isEqualTo(f.setup().standard(0));
		assertThat(jdbc.queryForObject("select division_id from student_enrollments where id = ?", Long.class,
				f.enrollmentId())).isEqualTo(f.setup().divisionByStandard().get(f.setup().standard(0)));
		assertThat(enrollmentAudits(f.enrollmentId())).isZero();
	}

	@Test
	void everyCombinationOfStandardCategoryAndDivisionIsRefusedWhenItChangesStandardOrCategory() throws Exception {
		Fixture f = finalisedYear("LcMatrix", 3745);
		SignedInClient client = SignedInClient.of(mvc);
		long yearId = f.setup().yearId();
		long[] standards = { f.setup().standard(0), f.setup().standard(1) };
		long[] categories = { f.setup().category(0), f.setup().category(1) };
		long[] divisionsOfFirst = { f.setup().divisionByStandard().get(standards[0]), f.divisionB() };
		int refused = 0;
		int allowed = 0;

		for (long standard : standards) {
			for (long category : categories) {
				long[] divisions = standard == standards[0] ? divisionsOfFirst
						: new long[] { f.setup().divisionByStandard().get(standard) };
				for (long division : divisions) {
					boolean sameStandardAndCategory = standard == standards[0] && category == categories[0];
					String json = body(f.enrollmentId(), standard, division, category);
					if (sameStandardAndCategory) {
						client.put(url(f.studentId(), yearId), json).andExpect(status().isOk());
						allowed++;
					}
					else {
						client.put(url(f.studentId(), yearId), json)
							.andExpect(status().isConflict())
							.andExpect(jsonPath("$.code").value("FEE_STRUCTURE_FINALISED"));
						refused++;
					}
				}
			}
		}

		assertThat(refused).isEqualTo(4);
		assertThat(allowed).isEqualTo(2);
		assertThat(jdbc.queryForObject("select standard_id from student_enrollments where id = ?", Long.class,
				f.enrollmentId())).isEqualTo(standards[0]);
		assertThat(jdbc.queryForObject("select category_id from student_enrollments where id = ?", Long.class,
				f.enrollmentId())).isEqualTo(categories[0]);
	}

}
