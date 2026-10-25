package com.admin.management.masterdata.academicyear;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AcademicYearRepository extends JpaRepository<AcademicYear, Long> {

	List<AcademicYear> findAllByOrderByStartDateDesc();

	/** The year whose dates (both ends included) contain the day; years cannot overlap, so there is at most one. */
	Optional<AcademicYear> findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqual(LocalDate day, LocalDate sameDay);

	/** Years that share at least one day with the range, other than the year with {@code excludeId}. */
	@Query("""
			select a from AcademicYear a
			where a.startDate <= :end and a.endDate >= :start and a.id <> :excludeId
			order by a.startDate
			""")
	List<AcademicYear> findOverlapping(@Param("start") LocalDate start, @Param("end") LocalDate end,
			@Param("excludeId") long excludeId);

}
