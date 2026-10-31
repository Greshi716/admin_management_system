package com.admin.management.fees;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One line of a student's receipts (contract ReceiptSummary). */
public record ReceiptSummaryDto(Long serialNo, String receiptNo, LocalDate receiptDate, Receipt.Type receiptType,
		Receipt.Mode paymentMode, BigDecimal total, ReceiptStatus status) {

	static ReceiptSummaryDto of(Receipt receipt, ReceiptMark mark) {
		return new ReceiptSummaryDto(receipt.getSerialNo(), receipt.getReceiptNo(), receipt.getReceiptDate(),
				receipt.getReceiptType(), receipt.getPaymentMode(), receipt.getTotal(), ReceiptStatus.of(mark));
	}

}
