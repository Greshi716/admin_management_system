package com.admin.management.fees;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** A student's receipts with their status, newest first (shared by the collection context and the profile). */
@Component
class ReceiptSummaryReader {

	private final ReceiptRepository receipts;

	private final ReceiptMarkRepository marks;

	ReceiptSummaryReader(ReceiptRepository receipts, ReceiptMarkRepository marks) {
		this.receipts = receipts;
		this.marks = marks;
	}

	@Transactional(readOnly = true)
	List<ReceiptSummaryDto> forStudent(Long studentId) {
		List<Receipt> found = receipts.findByStudentIdOrderBySerialNoDesc(studentId);
		if (found.isEmpty()) {
			return List.of();
		}
		Map<Long, ReceiptMark> byReceipt = marks
			.findByReceiptSerialNoIn(found.stream().map(Receipt::getSerialNo).toList())
			.stream()
			.collect(Collectors.toMap(ReceiptMark::getReceiptSerialNo, Function.identity()));
		return found.stream().map(r -> ReceiptSummaryDto.of(r, byReceipt.get(r.getSerialNo()))).toList();
	}

}
