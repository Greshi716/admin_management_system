package com.admin.management.yearchange;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.admin.management.masterdata.api.MasterDataLookup;
import com.admin.management.masterdata.api.MasterDataLookup.YearDates;
import com.admin.management.masterdata.api.MasterDataLookup.YearRef;
import com.admin.management.students.api.ClassRecordPromotion;
import com.admin.management.students.api.ClassRecordPromotion.PendingStandard;

/**
 * Reads whether the year-change promotion warning is active and standards still to promote (FR-006, FR-014).
 */
@Service
public class PromotionStatusService {

	private final MasterDataLookup masterData;

	private final ClassRecordPromotion classRecordPromotion;

	public PromotionStatusService(MasterDataLookup masterData, ClassRecordPromotion classRecordPromotion) {
		this.masterData = masterData;
		this.classRecordPromotion = classRecordPromotion;
	}

	@Transactional(readOnly = true)
	public YearChangeStatusDto getStatus() {
		Optional<YearRef> currentYearOpt = masterData.currentYear();
		if (currentYearOpt.isEmpty()) {
			return new YearChangeStatusDto(false, null, null, List.of());
		}

		YearRef currentYear = currentYearOpt.get();
		List<YearDates> earlier = masterData.earlierYears(currentYear.startDate());
		if (earlier.isEmpty()) {
			return new YearChangeStatusDto(false, currentYear.name(), null, List.of());
		}

		YearDates oldYear = earlier.get(0);
		String oldYearName = masterData.yearNames().get(oldYear.id());

		List<PendingStandard> pending = classRecordPromotion.pendingByStandard(oldYear.id(), currentYear.id());
		List<YearChangeStatusDto.PendingStandardDto> standards = pending.stream()
				.map(ps -> new YearChangeStatusDto.PendingStandardDto(ps.standardId(), ps.name(), ps.pending()))
				.toList();

		boolean active = !standards.isEmpty();
		return new YearChangeStatusDto(active, currentYear.name(), oldYearName, standards);
	}

}
