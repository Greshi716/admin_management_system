package com.admin.management.fees;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.admin.management.shared.CurrentUserProvider;
import com.admin.management.shared.web.CodedConflictException;
import com.admin.management.shared.web.NotFoundException;
import com.admin.management.shared.web.ValidationException;

/**
 * Marks a cheque receipt "Cheque not cleared" (FR-013). The receipt row is never touched: the mark is a separate record
 * and the views then leave the receipt out of the paid amounts. The order inside the one transaction follows the save of
 * a receipt (research R13): input checks, student lock, balance token, rules, mark, audit entry. A failure at any step
 * saves nothing. A mark is one-way: there is no method that removes it.
 */
@Service
class ReceiptMarkService {

	static final int MAX_REASON = 200;

	private final ReceiptRepository receipts;

	private final ReceiptMarkRepository marks;

	private final FeeBalanceService balances;

	private final FeeCollectionService collection;

	private final ReceiptService receiptService;

	private final FeesAudit audit;

	private final CurrentUserProvider currentUser;

	private final Clock clock;

	ReceiptMarkService(ReceiptRepository receipts, ReceiptMarkRepository marks, FeeBalanceService balances,
			FeeCollectionService collection, ReceiptService receiptService, FeesAudit audit,
			CurrentUserProvider currentUser, Clock clock) {
		this.receipts = receipts;
		this.marks = marks;
		this.balances = balances;
		this.collection = collection;
		this.receiptService = receiptService;
		this.audit = audit;
		this.currentUser = currentUser;
		this.clock = clock;
	}

	@Transactional
	ReceiptDto markNotCleared(Long serialNo, NotClearedInput in) {
		String reason = in == null || in.reason() == null ? "" : in.reason().trim();
		if (reason.isEmpty()) {
			throw new ValidationException("reason", "Enter the reason the cheque did not clear.");
		}
		if (reason.length() > MAX_REASON) {
			throw new ValidationException("reason", "The reason can be at most " + MAX_REASON + " characters.");
		}
		if (in.balanceVersion() == null || in.balanceVersion().isBlank()) {
			throw new ValidationException("balanceVersion", "The balance version is missing. Refresh the screen.");
		}
		Receipt receipt = receipts.findById(serialNo).orElseThrow(() -> new NotFoundException("Receipt", serialNo));
		Long studentId = receipt.getStudentId();
		balances.lockStudent(studentId);
		FeeCollectionService.Current current = collection.current(studentId);
		if (!balances.token(studentId, current.enrollmentId()).equals(in.balanceVersion())) {
			throw new CodedConflictException("BALANCE_CHANGED", null,
					"The balance has changed. Refresh to see the latest balance.", collection.context(studentId));
		}
		if (marks.findByReceiptSerialNo(serialNo).isPresent()) {
			throw new CodedConflictException("ALREADY_MARKED", null,
					"This receipt is already marked. A mark cannot be changed.", null);
		}
		if (receipt.getPaymentMode() != Receipt.Mode.CHEQUE) {
			throw new CodedConflictException("NOT_A_CHEQUE", null,
					"Only a receipt paid by cheque can be marked \"Cheque not cleared\".", null);
		}
		String before = paidUpToText(studentId, receipt);
		marks.saveAndFlush(new ReceiptMark(serialNo, ReceiptMark.Kind.CHEQUE_NOT_CLEARED, reason, receipt.getTotal(),
				currentUser.currentUserId().orElseThrow(), clock.instant()));
		audit.chequeNotCleared(serialNo, receipt.getTotal(), before, paidUpToText(studentId, receipt), reason);
		return receiptService.get(serialNo);
	}

	/** "Paid up to" of the receipt's own class record as text; null for a previous-years receipt or no fee. */
	private String paidUpToText(Long studentId, Receipt receipt) {
		FeeBalance.PaidUpToView view = balances.balance(studentId, receipt.getEnrollmentId()).paidUpTo();
		return view == null ? null : view.text();
	}

}
