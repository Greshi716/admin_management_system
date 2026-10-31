package com.admin.management.fees;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.admin.management.audit.AuditService;
import com.admin.management.fees.api.FeeStructureCopy;

/**
 * Copies the fee structure from a source year to a target year, shifting due dates by 1 year (FR-001, FR-002, FR-005).
 */
@Service
class FeeStructureCopyService implements FeeStructureCopy {

	private final FeeRowRepository feeRowRepository;
	private final FeeYearStatusRepository feeYearStatusRepository;
	private final AuditService auditService;

	FeeStructureCopyService(FeeRowRepository feeRowRepository, FeeYearStatusRepository feeYearStatusRepository,
			AuditService auditService) {
		this.feeRowRepository = feeRowRepository;
		this.feeYearStatusRepository = feeYearStatusRepository;
		this.auditService = auditService;
	}

	@Override
	@Transactional
	public int copyFeeStructure(long fromYearId, long toYearId) {
		List<FeeRow> sourceRows = feeRowRepository.findByAcademicYearIdOrderById(fromYearId);

		List<Map<String, Object>> auditRows = new ArrayList<>();
		int count = 0;

		for (FeeRow source : sourceRows) {
			LocalDate newDue = source.getDueDate() == null ? null : source.getDueDate().plusYears(1);
			FeeRow newRow = new FeeRow(toYearId, source.getStandardId(), source.getCategoryId(),
					source.getAmount(), source.getRegistrationFee(), newDue);
			feeRowRepository.save(newRow);

			Map<String, Object> auditRow = new LinkedHashMap<>();
			auditRow.put("standardId", source.getStandardId());
			auditRow.put("categoryId", source.getCategoryId());
			auditRow.put("amount", source.getAmount());
			auditRow.put("registrationFee", source.getRegistrationFee());
			auditRow.put("dueDate", newDue == null ? null : newDue.toString());
			auditRows.add(auditRow);
			count++;
		}

		// Ensure draft status exists for the new year
		feeYearStatusRepository.save(new FeeYearStatus(toYearId));

		// Write Fees audit entry listing every copied fee row
		Map<String, Object> changes = new LinkedHashMap<>();
		changes.put("sourceYearId", fromYearId);
		changes.put("feeRows", auditRows);
		changes.put("count", count);

		auditService.record(FeesAudit.AREA, "Create", FeesAudit.FEE_STRUCTURE + "#" + toYearId, changes, null);

		return count;
	}

}
