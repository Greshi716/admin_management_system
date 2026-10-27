package com.admin.management.fees;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;

/** Declares only what the feature needs: there is no delete method, accounts are never removed (constitution IV). */
public interface FeeAccountRepository extends Repository<FeeAccount, Long> {

	<S extends FeeAccount> S save(S account);

	<S extends FeeAccount> S saveAndFlush(S account);

	Optional<FeeAccount> findById(Long enrollmentId);

	List<FeeAccount> findByAcademicYearId(Long academicYearId);

	long countByAcademicYearId(Long academicYearId);

}
