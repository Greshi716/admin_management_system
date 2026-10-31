package com.admin.management.masterdata.division;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.admin.management.masterdata.academicyear.AcademicYear;
import com.admin.management.masterdata.academicyear.AcademicYearRepository;
import com.admin.management.masterdata.api.DivisionCopy;

/**
 * Copies divisions from a source academic year into a target academic year (FR-001, FR-004).
 */
@Service
class DivisionCopyService implements DivisionCopy {

	private final DivisionRepository divisionRepository;
	private final AcademicYearRepository academicYearRepository;

	DivisionCopyService(DivisionRepository divisionRepository, AcademicYearRepository academicYearRepository) {
		this.divisionRepository = divisionRepository;
		this.academicYearRepository = academicYearRepository;
	}

	@Override
	@Transactional
	public int copyDivisions(long fromYearId, long toYearId) {
		AcademicYear targetYear = academicYearRepository.findById(toYearId)
				.orElseThrow(() -> new IllegalArgumentException("Target academic year not found: " + toYearId));

		List<Division> sourceDivisions = divisionRepository.findAllByYear(fromYearId);
		int copied = 0;
		for (Division div : sourceDivisions) {
			Division newDiv = new Division(targetYear, div.getStandard(), div.getName());
			newDiv.setActive(div.isActive());
			divisionRepository.save(newDiv);
			copied++;
		}
		return copied;
	}

}
