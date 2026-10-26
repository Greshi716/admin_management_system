package com.admin.management.masterdata.division;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DivisionRepository extends JpaRepository<Division, Long> {

	/** Every division of one academic year, with its standard loaded. */
	@Query("select d from Division d join fetch d.standard where d.academicYear.id = :yearId")
	List<Division> findAllByYear(@Param("yearId") Long yearId);

}
