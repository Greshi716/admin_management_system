package com.admin.management.students;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Declares only what the feature needs: there is no delete method (constitution IV). */
public interface StudentRepository extends Repository<Student, Long> {

	<S extends Student> S save(S student);

	Optional<Student> findById(Long id);

	/**
	 * Students (any status) whose surname, first name and middle name equal the given ones, ignoring capitals and
	 * spaces at both ends (FR-003). The arguments must already be trimmed and lower-case.
	 */
	@Query("""
			select s from Student s
			where lower(trim(s.surname)) = :surname and lower(trim(s.firstName)) = :firstName
			  and lower(trim(coalesce(s.middleName, ''))) = :middleName and s.id <> :excludeId
			order by s.id
			""")
	List<Student> findNameMatches(@Param("surname") String surname, @Param("firstName") String firstName,
			@Param("middleName") String middleName, @Param("excludeId") long excludeId);

}
