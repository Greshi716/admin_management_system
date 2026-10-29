package com.admin.management.fees;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.admin.management.shared.web.ApiProblem.FieldError;
import com.admin.management.shared.web.CodedConflictException;
import com.admin.management.shared.web.ValidationException;

/**
 * The rules a receipt must pass before it is saved, in the order of research R13: the fee state of the class record,
 * the order rule (earlier years first), the field rules, then the amounts against what is still due. The save and the
 * preview use the same code, so the form never shows a number the save would refuse. Nothing is rounded: an amount
 * with more than two decimals is refused (FR-023).
 */
@Component
class ReceiptRules {

	static final int MAX_RECEIPT_NO = 20;

	static final int MAX_CHEQUE_NO = 50;

	static final int MAX_BANK_NAME = 100;

	/** The checked amounts of a receipt, both with two decimals. */
	record Amounts(BigDecimal registration, BigDecimal fee) {

		BigDecimal total() {
			return registration.add(fee);
		}

	}

	/**
	 * @param complete true for a save: the receipt number, date, mode and cheque details are checked too. A preview
	 * (false) leaves them out, because they cannot change a number and the form is not complete while it is typed.
	 */
	Amounts check(FeeCollectionService.Current current, FeeBalance balance, ReceiptInput in, LocalDate today,
			boolean complete) {
		if (in.receiptType() == null) {
			throw new ValidationException("receiptType", "Choose the type of receipt.");
		}
		if (in.receiptType() == Receipt.Type.PREVIOUS_YEARS) {
			throw new ValidationException("receiptType", "Previous-years receipts are not available yet.");
		}
		checkState(current);
		if (balance.earlierYearsPending().signum() > 0) {
			throw new CodedConflictException("BROUGHT_FORWARD_PENDING", null,
					"Earlier years' pending of " + balance.earlierYearsPending().toPlainString()
							+ " must be paid first.",
					Map.of("pending", balance.earlierYearsPending()));
		}
		List<FieldError> errors = new ArrayList<>();
		BigDecimal registration = amount("registrationPaid", in.registrationPaid(), errors);
		BigDecimal fee = amount("feePaid", in.feePaid(), errors);
		boolean registrationOffered = balance.registrationFee().signum() > 0;
		if (registration.signum() > 0 && !registrationOffered) {
			errors.add(new FieldError("registrationPaid", "This student has no registration fee."));
		}
		if (errors.isEmpty() && complete && registration.signum() == 0 && fee.signum() == 0) {
			errors.add(new FieldError("feePaid", "Enter an amount above zero."));
			if (registrationOffered) {
				errors.add(new FieldError("registrationPaid", "Enter an amount above zero."));
			}
		}
		if (complete) {
			checkNumberDateAndMode(in, today, errors);
		}
		if (!errors.isEmpty()) {
			throw new ValidationException(errors);
		}
		if (registration.compareTo(balance.registrationDue()) > 0) {
			throw overpayment("registrationPaid", "registration fee", balance.registrationDue());
		}
		if (fee.compareTo(balance.yearFeeDue()) > 0) {
			throw overpayment("feePaid", "year fee", balance.yearFeeDue());
		}
		return new Amounts(registration, fee);
	}

	private static void checkState(FeeCollectionService.Current current) {
		switch (current.state()) {
			case FeeCollectionDto.NO_CURRENT_YEAR -> throw new CodedConflictException(FeeCollectionDto.NO_CURRENT_YEAR,
					null, "There is no academic year for today's date.", null);
			case FeeCollectionDto.NO_CLASS_RECORD -> throw new CodedConflictException(FeeCollectionDto.NO_CLASS_RECORD,
					null, "This student has no class record in the current year.", null);
			case FeeCollectionDto.FEE_NOT_FINALISED -> throw new CodedConflictException(
					FeeCollectionDto.FEE_NOT_FINALISED, null, "The fee structure for this year is not finalised.", null);
			case FeeCollectionDto.FEE_NOT_SET -> throw new CodedConflictException(FeeCollectionDto.FEE_NOT_SET, null,
					"Fee not set for this student's standard and category.", null);
			default -> {
			}
		}
	}

	private static void checkNumberDateAndMode(ReceiptInput in, LocalDate today, List<FieldError> errors) {
		String number = in.receiptNo() == null ? "" : in.receiptNo().trim();
		if (number.isEmpty()) {
			errors.add(new FieldError("receiptNo", "Enter the receipt no. from the receipt book."));
		}
		else if (number.length() > MAX_RECEIPT_NO) {
			errors.add(new FieldError("receiptNo", "The receipt no. can have at most " + MAX_RECEIPT_NO + " characters."));
		}
		if (in.receiptDate() == null) {
			errors.add(new FieldError("receiptDate", "Enter the receipt date."));
		}
		else if (in.receiptDate().isAfter(today)) {
			errors.add(new FieldError("receiptDate", "The receipt date cannot be in the future."));
		}
		if (in.paymentMode() == null) {
			errors.add(new FieldError("paymentMode", "Choose Cash or Cheque."));
		}
		else if (in.paymentMode() == Receipt.Mode.CHEQUE) {
			text("chequeNo", in.chequeNo(), MAX_CHEQUE_NO, "Enter the cheque no.", errors);
			text("bankName", in.bankName(), MAX_BANK_NAME, "Enter the bank name.", errors);
			if (in.chequeDate() == null) {
				errors.add(new FieldError("chequeDate", "Enter the cheque date."));
			}
		}
		else {
			if (notBlank(in.chequeNo())) {
				errors.add(new FieldError("chequeNo", "A cash receipt has no cheque no."));
			}
			if (notBlank(in.bankName())) {
				errors.add(new FieldError("bankName", "A cash receipt has no bank name."));
			}
			if (in.chequeDate() != null) {
				errors.add(new FieldError("chequeDate", "A cash receipt has no cheque date."));
			}
		}
	}

	private static void text(String field, String value, int max, String missing, List<FieldError> errors) {
		if (!notBlank(value)) {
			errors.add(new FieldError(field, missing));
		}
		else if (value.trim().length() > max) {
			errors.add(new FieldError(field, "Use at most " + max + " characters."));
		}
	}

	private static boolean notBlank(String value) {
		return value != null && !value.isBlank();
	}

	/** An amount is zero or more with at most two decimals; an empty one is 0.00. */
	private static BigDecimal amount(String field, BigDecimal value, List<FieldError> errors) {
		if (value == null) {
			return FeeBalance.ZERO;
		}
		String problem = Money.problem(value);
		if (problem != null) {
			errors.add(new FieldError(field, problem));
			return FeeBalance.ZERO;
		}
		return Money.scale2(value);
	}

	private static CodedConflictException overpayment(String field, String what, BigDecimal due) {
		return new CodedConflictException("OVERPAYMENT", field,
				"Only " + due.toPlainString() + " is still due for the " + what + ".", Map.of("due", due));
	}

}
