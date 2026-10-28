package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.admin.management.shared.web.CodedConflictException;
import com.admin.management.students.EnrollmentInput;
import com.admin.management.students.StudentService;
import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.FeeTestData.Setup;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignedInClient;

/**
 * Once a year's fee structure is finalised the standard and category of its class records are fixed; the division can
 * still change (FR-008, SC-005). Draft years and years with no structure keep the F1 rules.
 */
@Covers("FR-008")
class FeeLockIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	StudentService students;

	@Autowired
	FeeStructureService structures;

	@Autowired
	PlatformTransactionManager transactionManager;

	private final ExecutorService executor = Executors.newCachedThreadPool();

	@AfterAll
	void stopExecutor() {
		executor.shutdownNow();
	}

	/** Two standards, a row for the first, one student in standard 1 with category 0, and a second division B there. */
	private record Fixture(Setup setup, long studentId, long enrollmentId, long divisionB, long version) {
	}

	private Fixture fixture(String prefix, int yearNumber, String structure) throws Exception {
		Setup setup = FeeTestData.year(jdbc, prefix, LocalDate.of(yearNumber, 4, 1), 2);
		FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(0), null, "36000", "500", null);
		if (structure != null) {
			FeeTestData.setStatus(jdbc, setup.yearId(), false);
		}
		long enrollment = FeeTestData.student(jdbc, setup, yearNumber * 10, 0, 0);
		long studentId = jdbc.queryForObject("select student_id from student_enrollments where id = ?", Long.class,
				enrollment);
		long divisionB = jdbc.queryForObject(
				"insert into divisions (academic_year_id, standard_id, name) values (?, ?, 'B') returning id",
				Long.class, setup.yearId(), setup.standard(0));
		if ("Finalised".equals(structure)) {
			SignedInClient.of(mvc).post("/api/fee-structures/" + setup.yearId() + "/finalise", "{\"version\":0}")
				.andExpect(status().isOk());
		}
		long version = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class, enrollment);
		return new Fixture(setup, studentId, enrollment, divisionB, version);
	}

	private String body(Fixture f, long standardId, long divisionId, long categoryId) {
		return "{\"version\":" + f.version() + ",\"standardId\":" + standardId + ",\"divisionId\":" + divisionId
				+ ",\"categoryId\":" + categoryId + "}";
	}

	private String url(Fixture f) {
		return "/api/students/" + f.studentId() + "/enrollments/" + f.setup().yearId();
	}

	private long standardOf(Fixture f) {
		return jdbc.queryForObject("select standard_id from student_enrollments where id = ?", Long.class, f.enrollmentId());
	}

	private long categoryOf(Fixture f) {
		return jdbc.queryForObject("select category_id from student_enrollments where id = ?", Long.class, f.enrollmentId());
	}

	private long enrollmentAudits(Fixture f) {
		return jdbc.queryForObject("select count(*) from audit_log where entity = ? and action = 'Edit'", Long.class,
				"StudentEnrollment#" + f.enrollmentId());
	}

	@Test
	void inAFinalisedYearTheStandardAndTheCategoryCannotBeChanged() throws Exception {
		Fixture f = fixture("LockFin", 3601, "Finalised");
		SignedInClient client = SignedInClient.of(mvc);

		client.put(url(f), body(f, f.setup().standard(1), f.setup().divisionByStandard().get(f.setup().standard(1)),
				f.setup().category(0)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("FEE_STRUCTURE_FINALISED"))
			.andExpect(jsonPath("$.fieldErrors[0].field").value("standardId"));
		client.put(url(f), body(f, f.setup().standard(0), f.setup().divisionByStandard().get(f.setup().standard(0)),
				f.setup().category(1)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("FEE_STRUCTURE_FINALISED"));

		assertThat(standardOf(f)).isEqualTo(f.setup().standard(0));
		assertThat(categoryOf(f)).isEqualTo(f.setup().category(0));
		assertThat(enrollmentAudits(f)).isZero();
	}

	@Test
	void inAFinalisedYearTheDivisionCanStillBeChanged() throws Exception {
		Fixture f = fixture("LockDiv", 3602, "Finalised");

		SignedInClient.of(mvc).put(url(f), body(f, f.setup().standard(0), f.divisionB(), f.setup().category(0)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.divisionId").value(f.divisionB()))
			.andExpect(jsonPath("$.feeLocked").value(true));

		assertThat(enrollmentAudits(f)).isEqualTo(1);
	}

	@Test
	void inADraftYearAndInAYearWithNoStructureTheStandardAndCategoryChangeAsInF1() throws Exception {
		Fixture draft = fixture("LockDraft", 3603, "Draft");
		SignedInClient.of(mvc).put(url(draft), body(draft, draft.setup().standard(1),
				draft.setup().divisionByStandard().get(draft.setup().standard(1)), draft.setup().category(1)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.feeLocked").value(false));

		Fixture none = fixture("LockNone", 3604, null);
		SignedInClient.of(mvc).put(url(none), body(none, none.setup().standard(1),
				none.setup().divisionByStandard().get(none.setup().standard(1)), none.setup().category(1)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.feeLocked").value(false));

		assertThat(standardOf(draft)).isEqualTo(draft.setup().standard(1));
		assertThat(standardOf(none)).isEqualTo(none.setup().standard(1));
	}

	@Test
	void theClassHistoryOfTheProfileCarriesTheFlag() throws Exception {
		Fixture locked = fixture("LockFlagA", 3605, "Finalised");
		Fixture open = fixture("LockFlagB", 3606, "Draft");

		SignedInClient.of(mvc).get("/api/students/" + locked.studentId())
			.andExpect(jsonPath("$.classHistory[0].feeLocked").value(true));
		SignedInClient.of(mvc).get("/api/students/" + open.studentId())
			.andExpect(jsonPath("$.classHistory[0].feeLocked").value(false));
	}

	@Test
	void aStaleVersionIsReportedBeforeTheLock() throws Exception {
		Fixture f = fixture("LockStale", 3607, "Finalised");
		String stale = "{\"version\":" + (f.version() + 5) + ",\"standardId\":" + f.setup().standard(1)
				+ ",\"divisionId\":" + f.setup().divisionByStandard().get(f.setup().standard(1)) + ",\"categoryId\":"
				+ f.setup().category(0) + "}";

		SignedInClient.of(mvc).put(url(f), stale)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_VERSION"));
	}

	@Test
	void aChangeThatWaitsForARunningFinaliseIsRefusedAfterwards() throws Exception {
		Fixture f = fixture("LockRace", 3608, "Draft");
		long otherStandard = f.setup().standard(1);
		long otherDivision = f.setup().divisionByStandard().get(otherStandard);
		TransactionTemplate tx = new TransactionTemplate(transactionManager);
		Future<Object> change = tx.execute(status -> {
			// Finalised but not committed: the change must wait for this transaction and then see the lock.
			structures.finalise(f.setup().yearId(), 0L);
			Future<Object> running = executor.submit(() -> {
				try {
					students.changeEnrollment(f.studentId(), f.setup().yearId(),
							new EnrollmentInput(f.version(), otherStandard, otherDivision, f.setup().category(0)));
					return "changed";
				}
				catch (CodedConflictException refused) {
					return refused.getCode();
				}
			});
			awaitBlockedSession();
			assertThat(running).isNotDone();
			return running;
		});

		assertThat(change.get(30, TimeUnit.SECONDS)).isEqualTo("FEE_STRUCTURE_FINALISED");
		assertThat(standardOf(f)).isEqualTo(f.setup().standard(0));
	}

	/** Waits until another database session is waiting for a lock; fails after 30 seconds. */
	private void awaitBlockedSession() {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
		while (System.nanoTime() < deadline) {
			Integer waiting = jdbc.queryForObject("""
					select count(*) from pg_stat_activity
					where datname = current_database() and wait_event_type = 'Lock' and pid <> pg_backend_pid()
					""", Integer.class);
			if (waiting != null && waiting > 0) {
				return;
			}
			LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
		}
		fail("no database session started waiting for a lock");
	}

}
