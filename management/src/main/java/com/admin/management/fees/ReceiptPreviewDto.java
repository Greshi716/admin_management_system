package com.admin.management.fees;

import java.math.BigDecimal;

/**
 * A receipt as it would be saved: the checked registration and year (or earlier years') fee amounts, the total, the
 * total in words and the student's balance afterwards. The confirmation dialog shows these values, not the browser's.
 */
public record ReceiptPreviewDto(BigDecimal registration, BigDecimal fee, BigDecimal total, String totalWords,
		FeeBalance balanceAfter) {
}
