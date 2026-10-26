package com.admin.management.students;

import org.springframework.data.repository.Repository;

/** Every GR No. ever given. Declares only what the feature needs: there is no delete method (constitution IV). */
public interface GrNumberRepository extends Repository<StudentGrNumber, String> {

	<S extends StudentGrNumber> S save(S grNumber);

	boolean existsById(String grKey);

}
