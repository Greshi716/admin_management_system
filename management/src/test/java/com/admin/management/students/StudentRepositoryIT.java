package com.admin.management.students;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;

/** The mapping of the students tables, the no-delete rule and the audit writer (constitution IV). */
@Covers("FR-001")
@Covers("FR-010")
@Covers("FR-012")
class StudentRepositoryIT extends IntegrationTest {

	@Autowired
	StudentRepository students;

	@Autowired
	EnrollmentRepository enrollments;

	@Autowired
	GrNumberRepository grNumbers;

	@Autowired
	PhotoRepository photos;

	@Autowired
	StudentAudit studentAudit;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PlatformTransactionManager transactionManager;

	long standardId;

	long categoryId;

	long yearId;

	long divisionId;

	@BeforeAll
	void masterData() {
		standardId = jdbc.queryForObject("insert into standards (name, display_order) values ('Standard 1', 1) returning id",
				Long.class);
		categoryId = jdbc.queryForObject("select min(id) from student_categories", Long.class);
		yearId = jdbc.queryForObject(
				"insert into academic_years (name, start_date, end_date) values ('2026-27', '2026-04-01', '2027-03-31') returning id",
				Long.class);
		divisionId = jdbc.queryForObject(
				"insert into divisions (academic_year_id, standard_id, name) values (?, ?, 'A') returning id", Long.class,
				yearId, standardId);
	}

	private StudentDetails details(String grNo) {
		return new StudentDetails(grNo, "Testsurname", "Alpha", null, "Male", LocalDate.of(2012, 1, 1), null, null, null,
				"9000000001", null, "Test address", "123456789012", "Test religion 77", LocalDate.of(2020, 6, 1),
				standardId, null);
	}

	private <T> T inTransaction(java.util.function.Supplier<T> work) {
		return new TransactionTemplate(transactionManager).execute(status -> work.get());
	}

	@Test
	void studentEnrollmentAndGrKeyAreMappedAndTheDatabaseFillsItsDefaults() {
		Student saved = inTransaction(() -> {
			Student student = students.save(new Student(details("GR-MAP-1"), categoryId));
			grNumbers.save(new StudentGrNumber(" GR-MAP-1 ", student.getId(), Instant.now()));
			enrollments.save(new StudentEnrollment(student.getId(), yearId, standardId, divisionId, categoryId,
					LocalDate.of(2026, 4, 1)));
			return student;
		});

		Student loaded = inTransaction(() -> students.findById(saved.getId()).orElseThrow());
		assertThat(loaded.getGrNo()).isEqualTo("GR-MAP-1");
		assertThat(loaded.getSrNo()).isPositive();
		assertThat(loaded.getCreatedAt()).isNotNull();
		assertThat(loaded.getStatus()).isEqualTo("Active");
		assertThat(loaded.getSource()).isEqualTo("Manual");
		assertThat(loaded.getVersion()).isZero();
		assertThat(grNumbers.existsById("gr-map-1")).isTrue();
		StudentEnrollment enrollment = enrollments.findByStudentIdAndAcademicYearId(saved.getId(), yearId).orElseThrow();
		assertThat(enrollment.getYearStatus()).isEqualTo("Studying");
		assertThat(enrollment.getVersion()).isZero();
	}

	@Test
	void editingRaisesTheVersionAndMarkLeftIsKept() {
		Student saved = inTransaction(() -> students.save(new Student(details("GR-MAP-2"), categoryId)));

		inTransaction(() -> {
			Student student = students.findById(saved.getId()).orElseThrow();
			student.markLeft();
			return students.save(student);
		});

		Student loaded = inTransaction(() -> students.findById(saved.getId()).orElseThrow());
		assertThat(loaded.getStatus()).isEqualTo("Left");
		assertThat(loaded.getVersion()).isEqualTo(1);
	}

	@Test
	void photoBytesRoundTripAndCanBeRemoved() {
		Student saved = inTransaction(() -> students.save(new Student(details("GR-MAP-3"), categoryId)));
		byte[] bytes = { 1, 2, 3, (byte) 255, 0, 42 };

		inTransaction(() -> photos.save(new StudentPhoto(saved.getId(), bytes, "image/jpeg", Instant.now())));

		StudentPhoto loaded = inTransaction(() -> photos.findById(saved.getId()).orElseThrow());
		assertThat(loaded.getContent()).isEqualTo(bytes);
		assertThat(loaded.getSizeBytes()).isEqualTo(bytes.length);
		assertThat(loaded.getContentType()).isEqualTo("image/jpeg");

		new TransactionTemplate(transactionManager).executeWithoutResult(status -> photos.deleteById(saved.getId()));
		assertThat(photos.findById(saved.getId())).isEmpty();
	}

	@Test
	void toStringNeverPrintsAadhaarOrReligionCaste() {
		Student student = new Student(details("GR-MAP-4"), categoryId);

		assertThat(student.toString()).contains("GR-MAP-4").doesNotContain("123456789012").doesNotContain("Test religion");
		assertThat(student.details().toString()).doesNotContain("123456789012").doesNotContain("Test religion");
	}

	@Test
	void noStudentRepositoryHasADeleteMethod() {
		for (Class<?> repository : List.of(StudentRepository.class, EnrollmentRepository.class,
				GrNumberRepository.class)) {
			List<String> deletes = Arrays.stream(repository.getMethods())
				.map(Method::getName)
				.filter(name -> name.startsWith("delete") || name.startsWith("remove"))
				.toList();
			assertThat(deletes).as(repository.getSimpleName()).isEmpty();
		}
	}

	@Test
	void auditWritesEntriesInAreaStudentsWithOldAndNewValuesAndMaskedSensitiveFields() {
		Student saved = inTransaction(() -> students.save(new Student(details("GR-MAP-5"), categoryId)));
		Map<String, Object> before = saved.snapshot();
		StudentDetails changed = new StudentDetails("GR-MAP-5", "Testsurname", "Alpha", null, "Male",
				LocalDate.of(2012, 1, 1), null, null, null, "9000000002", null, "Test address", "999988887777",
				"Another caste", LocalDate.of(2020, 6, 1), standardId, null);
		Student after = new Student(changed, categoryId);

		inTransaction(() -> {
			studentAudit.created(StudentAudit.STUDENT, saved.getId(), before);
			studentAudit.edited(StudentAudit.STUDENT, saved.getId(), before, after.snapshot());
			studentAudit.edited(StudentAudit.STUDENT, saved.getId(), before, before);
			return null;
		});

		List<Map<String, Object>> rows = jdbc.queryForList(
				"select area, action, entity, changes::text as changes from audit_log where entity = ? order by id",
				"Student#" + saved.getId());
		assertThat(rows).hasSize(2);
		assertThat(rows.get(0)).containsEntry("area", "Students").containsEntry("action", "Create");
		assertThat(rows.get(1)).containsEntry("action", "Edit");
		String edit = (String) rows.get(1).get("changes");
		assertThat(edit).contains("\"old\"").contains("\"new\"").contains("mobile1").contains("9000000002")
			.contains("********7777").contains("*********aste");
		assertThat(rows.toString()).doesNotContain("123456789012").doesNotContain("999988887777")
			.doesNotContain("Test religion 77").doesNotContain("Another caste");
	}

	@Test
	void auditOutsideATransactionFails() {
		assertThatThrownBy(() -> studentAudit.created(StudentAudit.STUDENT, 1L, Map.of("grNo", "x")))
			.isInstanceOf(IllegalTransactionStateException.class);
	}

}
