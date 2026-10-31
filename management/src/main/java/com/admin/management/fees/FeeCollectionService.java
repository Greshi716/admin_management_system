package com.admin.management.fees;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.admin.management.masterdata.api.MasterDataLookup;
import com.admin.management.masterdata.api.MasterDataLookup.YearRef;
import com.admin.management.shared.web.NotFoundException;
import com.admin.management.students.api.EnrollmentLookup;
import com.admin.management.students.api.EnrollmentLookup.ClassRecordRef;
import com.admin.management.students.api.StudentLookup;
import com.admin.management.students.api.StudentLookup.StudentSummary;

/** The Collect fee context of a student and the preview of a receipt, both read-only (FR-008, FR-014, FR-016). */
@Service
class FeeCollectionService {

	private final StudentLookup students;

	private final EnrollmentLookup enrollments;

	private final MasterDataLookup masterData;

	private final FeeAccountRepository accounts;

	private final FeeBalanceService balances;

	private final ReceiptSummaryReader summaries;

	private final ReceiptRules rules;

	private final Clock clock;

	FeeCollectionService(StudentLookup students, EnrollmentLookup enrollments, MasterDataLookup masterData,
			FeeAccountRepository accounts, FeeBalanceService balances, ReceiptSummaryReader summaries, ReceiptRules rules, Clock clock) {
		this.students = students;
		this.enrollments = enrollments;
		this.masterData = masterData;
		this.accounts = accounts;
		this.balances = balances;
		this.summaries = summaries;
		this.rules = rules;
		this.clock = clock;
	}

	/** The student, the current year, the student's class record in it and that record's fee account. */
	record Current(StudentSummary student, Optional<YearRef> year, Optional<ClassRecordRef> record,
			Optional<FeeAccount> account) {

		/** Why a current-year receipt is not possible, or FEE_SET when it is. */
		String state() {
			if (year.isEmpty()) {
				return FeeCollectionDto.NO_CURRENT_YEAR;
			}
			if (record.isEmpty()) {
				return FeeCollectionDto.NO_CLASS_RECORD;
			}
			if (account.isEmpty()) {
				return FeeCollectionDto.FEE_NOT_FINALISED;
			}
			return account.get().getAmount() == null ? FeeCollectionDto.FEE_NOT_SET : FeeCollectionDto.FEE_SET;
		}

		Long enrollmentId() {
			return record.map(ClassRecordRef::enrollmentId).orElse(null);
		}

	}

	@Transactional(readOnly = true)
	Current current(Long studentId) {
		StudentSummary student = students.summary(studentId)
			.orElseThrow(() -> new NotFoundException("Student", studentId));
		Optional<YearRef> year = masterData.currentYear();
		Optional<ClassRecordRef> record = year.flatMap(y -> enrollments.byStudentAndYear(studentId, y.id()));
		Optional<FeeAccount> account = record.flatMap(r -> accounts.findById(r.enrollmentId()));
		return new Current(student, year, record, account);
	}

	@Transactional(readOnly = true)
	FeeCollectionDto context(Long studentId) {
		Current current = current(studentId);
		String state = current.state();
		FeeBalance balance = balances.balance(studentId, current.enrollmentId());
		boolean feeSet = FeeCollectionDto.FEE_SET.equals(state);
		boolean charges = feeSet && balance.registrationFee().signum() > 0;
		boolean canPrevious = balance.earlierYearsPending().signum() > 0;
		boolean canCurrent = feeSet && !canPrevious;
		String message = FeeCollectionDto.NO_CLASS_RECORD.equals(state)
				? "Not promoted yet: promote the student first" : null;
		FeeCollectionDto.YearPart yearPart = current.year()
			.map(y -> masterData.yearDates(y.id()).orElseThrow())
			.map(d -> new FeeCollectionDto.YearPart(d.id(), d.name(), d.startDate(), d.endDate(), state, message))
			.orElse(null);
		List<FeeCollectionDto.YearOption> earlier = current.year()
			.map(y -> masterData.earlierYears(y.startDate())
				.stream()
				.map(d -> new FeeCollectionDto.YearOption(d.id(), d.name()))
				.toList())
			.orElse(List.of());
		return new FeeCollectionDto(studentPart(current), yearPart, canCurrent, canPrevious, charges, earlier, balance,
				balances.token(studentId, current.enrollmentId()), summaries.forStudent(studentId), message,
				"brought forward");
	}

	/**
	 * The receipt as it would be saved, with the balance after it. Nothing is written. The same rules as the save
	 * (ReceiptRules). With {@code validate} false (the form while it is typed) the receipt number, date, mode and
	 * cheque details are left out, as they cannot change a number; true (the Save button) checks them too, so the
	 * confirmation dialog does not open for a receipt that is incomplete or invalid in itself. A receipt number already used is only checked by the save.
	 */
	@Transactional(readOnly = true)
	ReceiptPreviewDto preview(Long studentId, ReceiptInput input, boolean validate) {
		Current current = current(studentId);
		FeeBalance.Position position = balances.position(studentId, current.enrollmentId());
		ReceiptRules.Amounts amounts = rules.check(current, FeeBalance.of(position), input, LocalDate.now(clock), validate);
		BigDecimal total = amounts.total();
		FeeBalance after = FeeBalance.of(position.withReceipt(input.receiptType(), amounts.registration(), amounts.fee()));
		return new ReceiptPreviewDto(amounts.registration(), amounts.fee(), total, AmountInWords.of(total), after);
	}

	private FeeCollectionDto.StudentPart studentPart(Current current) {
		StudentSummary s = current.student();
		String stdDiv = current.record().map(r -> {
			String standard = masterData.standardNames().get(r.standardId());
			String division = r.divisionId() == null ? null : masterData.divisionNames().get(r.divisionId());
			return division == null ? standard : standard + " / " + division;
		}).orElse(null);
		return new FeeCollectionDto.StudentPart(s.id(), s.fullName(), s.grNo(), s.status(), stdDiv);
	}

}
