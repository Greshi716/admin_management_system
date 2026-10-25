package com.admin.management.masterdata.category;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentCategoryRepository extends JpaRepository<StudentCategory, Long> {

	/** Categories offered when choosing for a new record. */
	List<StudentCategory> findAllByActiveTrueOrderByIdAsc();

}
