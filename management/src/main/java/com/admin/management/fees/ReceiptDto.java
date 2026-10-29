package com.admin.management.fees;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** A receipt as the API returns it (contract Receipt): the stored values, the status and the school header. */
public record ReceiptDto(Long serialNo, String receiptNo, LocalDate receiptDate, Receipt.Type receiptType,
		Receipt.Mode paymentMode, BigDecimal total, ReceiptStatus status, Long studentId, String balanceVersion,
		String academicYear, String studentName, String grNo, String standardName, String divisionName,
		BigDecimal registrationPaid, BigDecimal feePaid, String totalWords, String chequeNo, LocalDate chequeDate,
		String bankName, String statusReason, Instant markedAt, School school) {

	/** From {@code campusflow.school.*}; address and phone may be empty. */
	public record School(String name, String address, String phone) {
	}

}
