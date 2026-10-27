package com.admin.management.fees;

import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.LockModeType;

/**
 * The lock methods must run inside the caller's transaction (they fail without one), because a lock that is released
 * at once protects nothing. On PostgreSQL the write lock is {@code FOR NO KEY UPDATE} and the read lock is
 * {@code FOR SHARE} (checked in task T002). There is no delete method: a status row is never removed.
 */
public interface FeeYearStatusRepository extends Repository<FeeYearStatus, Long> {

	<S extends FeeYearStatus> S save(S status);

	<S extends FeeYearStatus> S saveAndFlush(S status);

	Optional<FeeYearStatus> findById(Long academicYearId);

	/** Finalise and every row change take this lock; it waits for any other holder of either lock. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Transactional(propagation = Propagation.MANDATORY)
	@Query("select s from FeeYearStatus s where s.academicYearId = :yearId")
	Optional<FeeYearStatus> lockForUpdate(@Param("yearId") Long yearId);

	/** Adding a class record takes this lock: it waits for a running finalise or revise, not for another class record. */
	@Lock(LockModeType.PESSIMISTIC_READ)
	@Transactional(propagation = Propagation.MANDATORY)
	@Query("select s from FeeYearStatus s where s.academicYearId = :yearId")
	Optional<FeeYearStatus> lockForShare(@Param("yearId") Long yearId);

	/** The write lock, and the version goes up by one at once (every row change bumps the structure version). */
	@Lock(LockModeType.PESSIMISTIC_FORCE_INCREMENT)
	@Transactional(propagation = Propagation.MANDATORY)
	@Query("select s from FeeYearStatus s where s.academicYearId = :yearId")
	Optional<FeeYearStatus> lockForUpdateAndBump(@Param("yearId") Long yearId);

}
