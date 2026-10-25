package com.admin.management.masterdata.standard;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StandardRepository extends JpaRepository<Standard, Long> {

	/** Standards offered when choosing for a new record: active ones, in display order. */
	List<Standard> findAllByActiveTrueOrderByDisplayOrderAscNameAsc();

}
