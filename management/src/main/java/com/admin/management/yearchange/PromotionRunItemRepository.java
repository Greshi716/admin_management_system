package com.admin.management.yearchange;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;

/**
 * Repository for append-only promotion run items (FR-016, FR-025).
 */
public interface PromotionRunItemRepository extends Repository<PromotionRunItem, Long> {

	<S extends PromotionRunItem> S save(S item);

	Optional<PromotionRunItem> findById(Long id);

	Optional<PromotionRunItem> findByFromEnrollmentId(Long fromEnrollmentId);

	List<PromotionRunItem> findByRunId(Long runId);

}
