package com.admin.management.yearchange;

import java.util.Optional;

import org.springframework.data.repository.Repository;

/**
 * Repository for append-only promotion runs (FR-016).
 */
public interface PromotionRunRepository extends Repository<PromotionRun, Long> {

	<S extends PromotionRun> S save(S run);

	Optional<PromotionRun> findById(Long id);

}
