package com.admin.management.fees;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Declares only what the feature needs: there is no delete method, "clear" saves zero (constitution IV). */
public interface BroughtForwardRepository extends Repository<BroughtForward, Long> {

	<S extends BroughtForward> S save(S broughtForward);

	<S extends BroughtForward> S saveAndFlush(S broughtForward);

	Optional<BroughtForward> findById(Long studentId);

	/**
	 * Creates the student's first row unless another window already did; returns 1 when this call created it and 0
	 * when the row existed. A plain insert would abort the transaction on a race, so this one cannot.
	 */
	@Modifying(flushAutomatically = true)
	@Transactional(propagation = Propagation.MANDATORY)
	@Query(value = """
			insert into fee_brought_forward (student_id, amount, note, updated_at, version)
			values (:studentId, :amount, cast(:note as text), :now, 0)
			on conflict (student_id) do nothing
			""", nativeQuery = true)
	int insertIfAbsent(@Param("studentId") Long studentId, @Param("amount") BigDecimal amount,
			@Param("note") String note, @Param("now") Instant now);

}
