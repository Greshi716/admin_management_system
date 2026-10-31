package com.admin.management.yearchange;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.admin.management.audit.AuditService;
import com.admin.management.fees.api.FeeStructureCopy;
import com.admin.management.masterdata.api.AcademicYearCreator;
import com.admin.management.masterdata.api.DivisionCopy;
import com.admin.management.masterdata.api.MasterDataLookup;
import com.admin.management.masterdata.api.YearCopyHandler;
import com.admin.management.masterdata.api.YearCopyResult;
import com.admin.management.shared.web.ConflictException;

/**
 * Creates an academic year and copies the divisions and fee rows from the latest previous year in one transaction
 * (FR-001, FR-002, FR-004, FR-005).
 */
@Service
public class YearSetupService implements YearCopyHandler {

	private final AcademicYearCreator academicYearCreator;
	private final MasterDataLookup masterDataLookup;
	private final DivisionCopy divisionCopy;
	private final FeeStructureCopy feeStructureCopy;
	private final AuditService auditService;

	public YearSetupService(AcademicYearCreator academicYearCreator, MasterDataLookup masterDataLookup,
			DivisionCopy divisionCopy, FeeStructureCopy feeStructureCopy, AuditService auditService) {
		this.academicYearCreator = academicYearCreator;
		this.masterDataLookup = masterDataLookup;
		this.divisionCopy = divisionCopy;
		this.feeStructureCopy = feeStructureCopy;
		this.auditService = auditService;
	}

	@Override
	@Transactional
	public YearCopyResult createWithCopy(String name, LocalDate startDate, LocalDate endDate) {
		List<MasterDataLookup.YearDates> earlier = masterDataLookup.earlierYears(startDate);
		if (earlier.isEmpty()) {
			throw new ConflictException("copyFromLast", "No previous academic year exists to copy from.");
		}

		long fromYearId = earlier.get(0).id();
		long toYearId = academicYearCreator.createYear(name, startDate, endDate);

		int divisionsCopied = divisionCopy.copyDivisions(fromYearId, toYearId);
		int feeRowsCopied = feeStructureCopy.copyFeeStructure(fromYearId, toYearId);

		Map<String, Object> auditChanges = new LinkedHashMap<>();
		auditChanges.put("copiedFromYearId", fromYearId);
		auditChanges.put("divisionsCopied", divisionsCopied);
		auditChanges.put("feeRowsCopied", feeRowsCopied);

		auditService.record("Master data", "Create", "AcademicYear#" + toYearId, auditChanges, null);

		return new YearCopyResult(toYearId, fromYearId, divisionsCopied, feeRowsCopied);
	}

}
