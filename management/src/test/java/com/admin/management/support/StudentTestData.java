package com.admin.management.support;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Synthetic student data for tests (constitution VI): made-up names, 10-digit mobile numbers and 12-digit Aadhaar
 * numbers that do not belong to anyone. Writes with plain SQL so it works before the student entities exist and
 * stays fast for the 10,000-student performance test.
 */
public final class StudentTestData {

	private static final String[] SURNAMES = { "Testsurname", "Samplesurname", "Demosurname", "Mocksurname" };

	private static final String[] FIRST_NAMES = { "Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot" };

	/** The values a student row needs. Names are synthetic; build a variant with the {@code with...} methods. */
	public record StudentInput(String grNo, String surname, String firstName, String middleName, String gender,
			LocalDate dob, String mobile1, String address, String aadhaar, String religionCaste, long categoryId,
			LocalDate admissionDate, long admissionStandardId) {

		public StudentInput withGrNo(String value) {
			return new StudentInput(value, surname, firstName, middleName, gender, dob, mobile1, address, aadhaar,
					religionCaste, categoryId, admissionDate, admissionStandardId);
		}

		public StudentInput withName(String newSurname, String newFirstName, String newMiddleName) {
			return new StudentInput(grNo, newSurname, newFirstName, newMiddleName, gender, dob, mobile1, address,
					aadhaar, religionCaste, categoryId, admissionDate, admissionStandardId);
		}
	}

	/** The master data one class record points at. */
	public record YearSetup(long yearId, LocalDate yearStartDate, long standardId, long divisionId) {
	}

	private StudentTestData() {
	}

	/** A valid input; {@code n} makes the GR No., the name and the numbers different from other inputs. */
	public static StudentInput valid(int n, long categoryId, long standardId) {
		return new StudentInput("GR-" + n, SURNAMES[n % SURNAMES.length], FIRST_NAMES[n % FIRST_NAMES.length],
				"Middle" + n, n % 2 == 0 ? "Male" : "Female", LocalDate.of(2012, 1, 1).plusDays(n % 700),
				String.format("9%09d", n), "Test address " + n + ", Test Town", String.format("%012d", 100000000000L + n),
				"Test religion " + n, categoryId, LocalDate.of(2020, 6, 1).plusDays(n % 300), standardId);
	}

	/** Inserts one student with its GR key and no class record. Returns the student id. */
	public static long insertStudent(JdbcTemplate jdbc, StudentInput in) {
		long id = jdbc.queryForObject("""
				insert into students (gr_no, surname, first_name, middle_name, gender, dob, mobile_1, address,
				    aadhaar, religion_caste, category_id, admission_date, admission_standard_id)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id
				""", Long.class, in.grNo(), in.surname(), in.firstName(), in.middleName(), in.gender(),
				Date.valueOf(in.dob()), in.mobile1(), in.address(), in.aadhaar(), in.religionCaste(), in.categoryId(),
				Date.valueOf(in.admissionDate()), in.admissionStandardId());
		jdbc.update("insert into student_gr_numbers (gr_key, student_id, assigned_at) values (lower(trim(?)), ?, now())",
				in.grNo(), id);
		return id;
	}

	public static void insertEnrollment(JdbcTemplate jdbc, long studentId, YearSetup year, long categoryId) {
		jdbc.update("""
				insert into student_enrollments (student_id, academic_year_id, standard_id, division_id, category_id,
				    year_start_date)
				values (?, ?, ?, ?, ?, ?)
				""", studentId, year.yearId(), year.standardId(), year.divisionId(), categoryId,
				Date.valueOf(year.yearStartDate()));
	}

	/** A student whose only class record is in an earlier year (the list must show that record and its year). */
	public static long insertStudentWithOnlyEarlierYear(JdbcTemplate jdbc, StudentInput in, YearSetup earlierYear) {
		long id = insertStudent(jdbc, in);
		insertEnrollment(jdbc, id, earlierYear, in.categoryId());
		return id;
	}

	/**
	 * Generates {@code count} students (GR No. {@code GR-1} to {@code GR-count}) through batch inserts. Student
	 * number {@code i} gets the class records of the first {@code 2 + i % 2} entries of {@code years}, so with three
	 * years most students have 2 to 3 records; {@code years} is ordered oldest first.
	 */
	public static void generate(JdbcTemplate jdbc, int count, long categoryId, long standardId, List<YearSetup> years) {
		List<Object[]> students = new java.util.ArrayList<>(count);
		for (int i = 1; i <= count; i++) {
			StudentInput in = valid(i, categoryId, standardId);
			students.add(new Object[] { in.grNo(), in.surname(), in.firstName(), in.middleName(), in.gender(),
					Date.valueOf(in.dob()), in.mobile1(), in.address(), in.aadhaar(), in.religionCaste(),
					in.categoryId(), Date.valueOf(in.admissionDate()), in.admissionStandardId() });
		}
		jdbc.batchUpdate("""
				insert into students (gr_no, surname, first_name, middle_name, gender, dob, mobile_1, address,
				    aadhaar, religion_caste, category_id, admission_date, admission_standard_id)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", students);
		jdbc.update("""
				insert into student_gr_numbers (gr_key, student_id, assigned_at)
				select lower(gr_no), id, now() from students
				where lower(gr_no) not in (select gr_key from student_gr_numbers)
				""");
		List<Long> ids = jdbc.queryForList("select id from students order by id", Long.class);
		List<Object[]> enrollments = new java.util.ArrayList<>();
		int perStudentExtra = Math.min(years.size(), 3);
		for (int i = 0; i < ids.size(); i++) {
			int records = Math.min(perStudentExtra, 2 + i % 2);
			for (int y = 0; y < records; y++) {
				YearSetup year = years.get(y);
				enrollments.add(new Object[] { ids.get(i), year.yearId(), year.standardId(), year.divisionId(),
						categoryId, Date.valueOf(year.yearStartDate()) });
			}
		}
		jdbc.batchUpdate("""
				insert into student_enrollments (student_id, academic_year_id, standard_id, division_id, category_id,
				    year_start_date)
				values (?, ?, ?, ?, ?, ?)
				""", enrollments);
	}

}
