package com.admin.management.students;

import java.util.Optional;

import org.springframework.data.repository.Repository;

/** Declares only what the feature needs: there is no delete method (constitution IV). */
public interface StudentRepository extends Repository<Student, Long> {

	<S extends Student> S save(S student);

	Optional<Student> findById(Long id);

}
