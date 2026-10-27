package com.admin.management.fees;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;

/** Fee rows are configuration, so a row can be deleted (FR-001); the audit entry keeps what it held. */
public interface FeeRowRepository extends Repository<FeeRow, Long> {

	<S extends FeeRow> S save(S row);

	<S extends FeeRow> S saveAndFlush(S row);

	Optional<FeeRow> findById(Long id);

	void delete(FeeRow row);

	List<FeeRow> findByAcademicYearIdOrderById(Long academicYearId);

	/** A null {@code categoryId} finds the row for all categories of the standard. */
	Optional<FeeRow> findByAcademicYearIdAndStandardIdAndCategoryId(Long academicYearId, Long standardId,
			Long categoryId);

	long countByAcademicYearId(Long academicYearId);

}
