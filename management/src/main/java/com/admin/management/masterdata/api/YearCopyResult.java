package com.admin.management.masterdata.api;

/**
 * Result of creating an academic year with copyFromLast (contracts/openapi.yaml YearCopyResult).
 */
public record YearCopyResult(Long id, Long copiedFromYearId, int divisionsCopied, int feeRowsCopied) {
}
