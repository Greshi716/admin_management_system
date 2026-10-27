package com.admin.management.students;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;

/** Declares only what the feature needs: there is no delete method (constitution IV). */
public interface EnrollmentRepository extends Repository<StudentEnrollment, Long> {

	<S extends StudentEnrollment> S save(S enrollment);

	<S extends StudentEnrollment> S saveAndFlush(S enrollment);

	Optional<StudentEnrollment> findById(Long id);

	Optional<StudentEnrollment> findByStudentIdAndAcademicYearId(Long studentId, Long academicYearId);

	List<StudentEnrollment> findByStudentId(Long studentId);

	/** Every class record of the year in id order, whatever the student's status. */
	List<StudentEnrollment> findByAcademicYearIdOrderById(Long academicYearId);

	long countByAcademicYearId(Long academicYearId);

}
