package com.admin.management.fees;

import java.time.Clock;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.admin.management.shared.ClassRecordFees;

/**
 * What the students module asks of fees (FR-006, FR-008). Adding a class record takes the year's status row for
 * sharing, so it waits for a running finalise or revise (and the other way round) but not for another registration;
 * if the year is Finalised it creates the record's account in the caller's transaction.
 */
@Component
class FeeClassRecordHooks implements ClassRecordFees {

	private final FeeYearStatusRepository statuses;

	private final FeeRowRepository rows;

	private final FeeAccountRepository accounts;

	private final Clock clock;

	FeeClassRecordHooks(FeeYearStatusRepository statuses, FeeRowRepository rows, FeeAccountRepository accounts,
			Clock clock) {
		this.statuses = statuses;
		this.rows = rows;
		this.accounts = accounts;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public boolean isLocked(Long yearId) {
		return statuses.findById(yearId).map(FeeYearStatus::isFinalised).orElse(false);
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean lockedForChange(Long yearId) {
		return statuses.lockForShare(yearId).map(FeeYearStatus::isFinalised).orElse(false);
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void classRecordAdded(Long enrollmentId, Long yearId, Long standardId, Long categoryId) {
		boolean finalised = statuses.lockForShare(yearId).map(FeeYearStatus::isFinalised).orElse(false);
		if (!finalised) {
			return;
		}
		FeeSelection.Result fee = FeeSelection.of(rows.findByAcademicYearIdOrderById(yearId))
			.select(standardId, categoryId);
		accounts.saveAndFlush(new FeeAccount(enrollmentId, yearId, fee.amount(), fee.registrationFee(), fee.dueDate(),
				clock.instant()));
	}

}
