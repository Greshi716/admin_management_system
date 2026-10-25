package com.admin.management.students;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
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

	/**
	 * Sets the photo version without raising the student's edit version, so adding a photo never makes an open edit
	 * form stale (FR-018). Clears the persistence context so the next read sees the new value.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Student s set s.photoVersion = :version where s.id = :id")
	int setPhotoVersion(@Param("id") Long id, @Param("version") Long version);

}
