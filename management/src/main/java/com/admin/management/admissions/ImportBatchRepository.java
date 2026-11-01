package com.admin.management.admissions;

import java.util.Optional;

import org.springframework.data.repository.Repository;

/**
 * Repository for admission import batches (FR-028, FR-030).
 */
public interface ImportBatchRepository extends Repository<ImportBatch, Long> {

	<S extends ImportBatch> S save(S batch);

	Optional<ImportBatch> findById(Long id);

}
