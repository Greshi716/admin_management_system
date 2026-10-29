package com.admin.management.fees;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.admin.management.masterdata.api.MasterDataLookup;
import com.admin.management.shared.CurrentUserProvider;
import com.admin.management.shared.web.CodedConflictException;
import com.admin.management.shared.web.NotFoundException;
import com.admin.management.shared.web.ValidationException;
import com.admin.management.students.api.EnrollmentLookup.ClassRecordRef;

/**
 * Saves a current-year receipt (FR-001 to FR-011, FR-016, FR-021) and reads one back. The order inside the one
 * transaction is the one of research R13: student lock, balance token, fee state, order rule, field rules, amounts
 * against what is due, receipt number, insert, audit entry. A failure at any step saves nothing. There is no method
 * that changes or removes a receipt (FR-009).
 */
@Service
class ReceiptService {

	private final FeeCollectionService collection;

	private final FeeBalanceService balances;

	private final ReceiptRules rules;

	private final ReceiptRepository receipts;

	private final ReceiptMarkRepository marks;

	private final FeesAudit audit;

	private final MasterDataLookup masterData;

	private final CurrentUserProvider currentUser;

	private final SchoolProperties school;

	private final Clock clock;

	ReceiptService(FeeCollectionService collection, FeeBalanceService balances, ReceiptRules rules,
			ReceiptRepository receipts, ReceiptMarkRepository marks, FeesAudit audit, MasterDataLookup masterData,
			CurrentUserProvider currentUser, SchoolProperties school, Clock clock) {
		this.collection = collection;
		this.balances = balances;
		this.rules = rules;
		this.receipts = receipts;
		this.marks = marks;
		this.audit = audit;
		this.masterData = masterData;
		this.currentUser = currentUser;
		this.school = school;
		this.clock = clock;
	}

	@Transactional
	ReceiptDto create(ReceiptInput in) {
		if (in == null || in.studentId() == null) {
			throw new ValidationException("studentId", "Choose the student.");
		}
		if (in.receiptType() == null) {
			throw new ValidationException("receiptType", "Choose the type of receipt.");
		}
		if (in.balanceVersion() == null || in.balanceVersion().isBlank()) {
			throw new ValidationException("balanceVersion", "The balance version is missing. Refresh the screen.");
		}
		Long studentId = in.studentId();
		balances.lockStudent(studentId);
		FeeCollectionService.Current current = collection.current(studentId);
		if (!balances.token(studentId, current.enrollmentId()).equals(in.balanceVersion())) {
			throw new CodedConflictException("BALANCE_CHANGED", null,
					"The balance has changed. Refresh to see the latest balance.", collection.context(studentId));
		}
		FeeBalance balance = balances.balance(studentId, current.enrollmentId());
		LocalDate today = LocalDate.now(clock);
		ReceiptRules.Amounts amounts = rules.check(current, balance, in, today, true);
		String number = in.receiptNo().trim();
		rejectUsedNumber(number);

		ClassRecordRef record = current.record().orElseThrow();
		boolean cheque = in.paymentMode() == Receipt.Mode.CHEQUE;
		Receipt saved = receipts.saveAndFlush(new Receipt(new Receipt.Draft(number, in.receiptDate(),
				record.yearId(), studentId, record.enrollmentId(), current.student().fullName(),
				current.student().grNo(), record.standardId(), record.divisionId(), record.categoryId(),
				masterData.standardNames().get(record.standardId()),
				record.divisionId() == null ? null : masterData.divisionNames().get(record.divisionId()),
				Receipt.Type.CURRENT_YEAR, amounts.registration(), amounts.fee(), AmountInWords.of(amounts.total()),
				in.paymentMode(), cheque ? in.chequeNo().trim() : null, cheque ? in.bankName().trim() : null,
				cheque ? in.chequeDate() : null, currentUser.currentUserId().orElseThrow(),
				clock.instant())));
		audit.receiptCreated(saved);
		return view(saved);
	}

	@Transactional(readOnly = true)
	ReceiptDto get(Long serialNo) {
		Receipt receipt = receipts.findById(serialNo).orElseThrow(() -> new NotFoundException("Receipt", serialNo));
		return view(receipt);
	}

	/** A number is free again only after the developer voided the receipt that held it (FR-001, research R12). */
	private void rejectUsedNumber(String number) {
		for (Receipt existing : receipts.findByReceiptNoIgnoreCase(number)) {
			Optional<ReceiptMark> mark = marks.findByReceiptSerialNo(existing.getSerialNo());
			if (mark.isEmpty() || mark.get().getKind() != ReceiptMark.Kind.DEVELOPER_VOID) {
				throw new CodedConflictException("DUPLICATE_RECEIPT_NO", "receiptNo",
						"This receipt number is already used by the receipt of " + existing.getReceiptDate()
								+ " (serial no. " + existing.getSerialNo() + ").",
						null);
			}
		}
	}

	private ReceiptDto view(Receipt r) {
		Optional<ReceiptMark> mark = marks.findByReceiptSerialNo(r.getSerialNo());
		Long enrollmentId = collection.current(r.getStudentId()).enrollmentId();
		return new ReceiptDto(r.getSerialNo(), r.getReceiptNo(), r.getReceiptDate(), r.getReceiptType(),
				r.getPaymentMode(), r.getTotal(), ReceiptStatus.of(mark.orElse(null)), r.getStudentId(),
				balances.token(r.getStudentId(), enrollmentId), masterData.yearNames().get(r.getAcademicYearId()),
				r.getStudentName(), r.getGrNo(), r.getStandardName(), r.getDivisionName(), r.getRegistrationPaid(),
				r.getFeePaid(), r.getTotalWords(), r.getChequeNo(), r.getChequeDate(), r.getBankName(),
				mark.map(ReceiptMark::getReason).orElse(null), mark.map(ReceiptMark::getMarkedAt).orElse(null),
				new ReceiptDto.School(school.getName(), blankToNull(school.getAddress()),
						blankToNull(school.getPhone())));
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}

}
