package com.admin.management.students;

import java.util.Optional;

import org.springframework.data.repository.Repository;

/** The photo bytes may be removed (the only delete in the feature); the student itself never is. */
public interface PhotoRepository extends Repository<StudentPhoto, Long> {

	<S extends StudentPhoto> S save(S photo);

	Optional<StudentPhoto> findById(Long studentId);

	void deleteById(Long studentId);

}
