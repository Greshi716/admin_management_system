package com.admin.management.fees;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A receipt to save or preview (contract ReceiptInput). Amounts are read as exact decimals, so a value with too many
 * places is refused instead of rounded (FR-023). {@code balanceVersion} is the token from the fee-collection context.
 */
public record ReceiptInput(Long studentId, Receipt.Type receiptType, String receiptNo, LocalDate receiptDate,
		BigDecimal registrationPaid, BigDecimal feePaid, Long previousYearId, Receipt.Mode paymentMode, String chequeNo,
		LocalDate chequeDate, String bankName, String balanceVersion) {
}
