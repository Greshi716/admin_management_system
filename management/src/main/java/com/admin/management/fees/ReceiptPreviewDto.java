package com.admin.management.fees;

import java.math.BigDecimal;

/** A receipt as it would be saved: the total, the total in words and the student's balance afterwards. */
public record ReceiptPreviewDto(BigDecimal total, String totalWords, FeeBalance balanceAfter) {
}
