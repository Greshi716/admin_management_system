package com.admin.management.masterdata.division;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.admin.management.masterdata.ActiveInput;
import com.admin.management.masterdata.MasterDataAudit;
import com.admin.management.masterdata.Versions;
import com.admin.management.masterdata.academicyear.AcademicYear;
import com.admin.management.masterdata.academicyear.AcademicYearRepository;
import com.admin.management.masterdata.standard.Standard;
import com.admin.management.masterdata.standard.StandardRepository;
import com.admin.management.shared.web.NotFoundException;
import com.admin.management.shared.web.ValidationException;

/** Divisions: create, edit, delete when unused, mark active or inactive (FR-016, FR-017, FR-020, FR-021). */
@Service
public class DivisionService {

	static final String TYPE = "Division";

	private final DivisionRepository divisions;

	private final AcademicYearRepository years;

	private final StandardRepository standards;

	private final MasterDataAudit audit;

	DivisionService(DivisionRepository divisions, AcademicYearRepository years, StandardRepository standards,
			MasterDataAudit audit) {
		this.divisions = divisions;
		this.years = years;
		this.standards = standards;
		this.audit = audit;
	}

	@Transactional
	public DivisionDto create(DivisionDto input) {
		AcademicYear year = existingYear(input.academicYearId());
		Standard standard = existingStandard(input.standardId());
		if (!standard.isActive()) {
			throw inactiveStandard();
		}
		Division division = divisions.saveAndFlush(new Division(year, standard, input.name().trim()));
		audit.created(TYPE, division.getId(), division.snapshot());
		return DivisionDto.of(division);
	}

	@Transactional
	public DivisionDto update(Long id, DivisionDto input) {
		Division division = find(id);
		Versions.check(input.version(), division.getVersion(), () -> DivisionDto.of(division));
		AcademicYear year = existingYear(input.academicYearId());
		Standard standard = existingStandard(input.standardId());
		boolean standardChanged = !standard.getId().equals(division.getStandard().getId());
		if (standardChanged && !standard.isActive()) {
			throw inactiveStandard();
		}
		var before = division.snapshot();
		division.change(year, standard, input.name().trim());
		divisions.saveAndFlush(division);
		audit.edited(TYPE, id, before, division.snapshot());
		return DivisionDto.of(division);
	}

	@Transactional
	public DivisionDto setActive(Long id, ActiveInput input) {
		Division division = find(id);
		Versions.check(input.version(), division.getVersion(), () -> DivisionDto.of(division));
		var before = division.snapshot();
		division.setActive(input.active());
		divisions.saveAndFlush(division);
		audit.edited(TYPE, id, before, division.snapshot());
		return DivisionDto.of(division);
	}

	@Transactional
	public void delete(Long id) {
		Division division = find(id);
		var values = division.snapshot();
		divisions.delete(division);
		divisions.flush();
		audit.deleted(TYPE, id, values);
	}

	private Division find(Long id) {
		return divisions.findById(id).orElseThrow(() -> new NotFoundException("Division", id));
	}

	private AcademicYear existingYear(Long id) {
		return years.findById(id)
			.orElseThrow(() -> new ValidationException("academicYearId", "Choose an existing academic year."));
	}

	private Standard existingStandard(Long id) {
		return standards.findById(id)
			.orElseThrow(() -> new ValidationException("standardId", "Choose an existing standard."));
	}

	private static ValidationException inactiveStandard() {
		return new ValidationException("standardId", "This standard is inactive. Choose an active standard.");
	}

}
