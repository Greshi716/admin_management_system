package com.admin.management.students;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;

/** Declares only what the feature needs: there is no delete method (constitution IV). */
public interface EnrollmentRepository extends Repository<StudentEnrollment, Long> {

	<S extends StudentEnrollment> S save(S enrollment);

	Optional<StudentEnrollment> findByStudentIdAndAcademicYearId(Long studentId, Long academicYearId);

	List<StudentEnrollment> findByStudentId(Long studentId);

}
