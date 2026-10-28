package com.admin.management.fees;

import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

	/**
	 * Raises the version by one in the database. For a status row the caller has already loaded and locked: the
	 * lock-and-bump query below cannot raise the version of an entity that is already loaded. The loaded entity keeps
	 * its old version, so the caller must not change and save it afterwards.
	 */
	@Modifying(flushAutomatically = true)
	@Transactional(propagation = Propagation.MANDATORY)
	@Query("update FeeYearStatus s set s.version = s.version + 1 where s.academicYearId = :yearId")
	int bumpVersion(@Param("yearId") Long yearId);

	/**
	 * Creates the Draft status row of a year unless another window already did; returns 1 when this call created it
	 * (the row is then locked by this transaction) and 0 when it existed. It cannot abort the transaction on a race.
	 */
	@Modifying(flushAutomatically = true)
	@Transactional(propagation = Propagation.MANDATORY)
	@Query(value = "insert into fee_year_status (academic_year_id) values (:yearId) on conflict do nothing",
			nativeQuery = true)
	int insertIfAbsent(@Param("yearId") Long yearId);

}
