package com.admin.management.admissions;

import java.util.Optional;

import org.springframework.data.repository.Repository;

/**
 * Repository for admission requests (FR-030, FR-031, FR-033, FR-035, FR-036).
 */
public interface AdmissionRequestRepository extends Repository<AdmissionRequest, Long> {

	<S extends AdmissionRequest> S save(S request);

	Optional<AdmissionRequest> findById(Long id);

	Optional<AdmissionRequest> findByImportKey(String importKey);

	long countByStatus(String status);

}
