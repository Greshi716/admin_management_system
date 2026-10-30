package com.admin.management.fees;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.admin.management.masterdata.api.MasterDataLookup;
import com.admin.management.shared.web.NotFoundException;
import com.admin.management.students.api.EnrollmentLookup;
import com.admin.management.students.api.EnrollmentLookup.ClassRecordRef;
import com.admin.management.students.api.StudentLookup;

/** Reads the Fees block of the profile: the fee account of the class record it shows, the balance and the receipts (FR-005, FR-009, FR-014). */
@Service
class StudentFeesService {

	private static final BigDecimal ZERO = new BigDecimal("0.00");

	private final StudentLookup students;

	private final EnrollmentLookup enrollments;

	private final FeeAccountRepository accounts;

	private final BroughtForwardRepository broughtForwards;

	private final MasterDataLookup masterData;

	private final FeeBalanceService balances;

	private final ReceiptSummaryReader summaries;

	StudentFeesService(StudentLookup students, EnrollmentLookup enrollments, FeeAccountRepository accounts,
			BroughtForwardRepository broughtForwards, MasterDataLookup masterData, FeeBalanceService balances,
			ReceiptSummaryReader summaries) {
		this.balances = balances;
		this.summaries = summaries;
		this.students = students;
		this.enrollments = enrollments;
		this.accounts = accounts;
		this.broughtForwards = broughtForwards;
		this.masterData = masterData;
	}

	/** The fees for the year of the record the profile shows, or for {@code yearId} when given. */
	@Transactional(readOnly = true)
	StudentFeesDto fees(Long studentId, Long yearId) {
		if (!students.exists(studentId)) {
			throw new NotFoundException("Student", studentId);
		}
		StudentFeesDto.BroughtForwardDto broughtForward = broughtForwards.findById(studentId)
			.map(b -> new StudentFeesDto.BroughtForwardDto(b.getVersion(), Money.scale2(b.getAmount()), b.getNote()))
			.orElse(new StudentFeesDto.BroughtForwardDto(null, ZERO, null));
		Optional<ClassRecordRef> record = yearId == null ? enrollments.shownRecord(studentId)
				: enrollments.byStudentAndYear(studentId, yearId);
		FeeBalance balance = balances.balance(studentId, record.map(ClassRecordRef::enrollmentId).orElse(null));
		List<ReceiptSummaryDto> receipts = summaries.forStudent(studentId);
		Long shownYear = record.map(ClassRecordRef::yearId).orElse(yearId);
		String yearName = shownYear == null ? null : masterData.yearNames().get(shownYear);
		Optional<FeeAccount> account = record.flatMap(r -> accounts.findById(r.enrollmentId()));
		if (account.isEmpty()) {
			return new StudentFeesDto(shownYear, yearName, StudentFeesDto.NOT_FINALISED, null, null, null, null,
					broughtForward, broughtForward.amount(), balance, receipts);
		}
		FeeAccount a = account.get();
		if (a.getAmount() == null) {
			return new StudentFeesDto(shownYear, yearName, StudentFeesDto.FEE_NOT_SET, null, null, null, null,
					broughtForward, broughtForward.amount(), balance, receipts);
		}
		BigDecimal totalFee = FeeSelection.totalFee(a.getAmount(), a.getRegistrationFee());
		return new StudentFeesDto(shownYear, yearName, StudentFeesDto.FEE_SET, a.getAmount(), a.getRegistrationFee(),
				totalFee, a.getDueDate(), broughtForward, totalFee.add(broughtForward.amount()), balance, receipts);
	}

}
