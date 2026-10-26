package com.admin.management.audit;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Package-private use only: other modules write through {@link AuditService}. */
interface AuditRepository extends JpaRepository<AuditEntry, Long>, JpaSpecificationExecutor<AuditEntry> {

	/** Newest first; served by the {@code (entity, at)} index. */
	List<AuditEntry> findByAreaAndEntityInOrderByAtDescIdDesc(String area, Collection<String> entities,
			Pageable page);

}
