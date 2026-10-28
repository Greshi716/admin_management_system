package com.admin.management.fees;

import java.math.BigDecimal;

/**
 * The brought-forward amount to save. {@code version} is null the first time (the row does not exist yet). The amount
 * is read as an exact decimal, so a value with too many places can be refused instead of rounded. "Clear" is an amount
 * of 0 with no note.
 */
public record BroughtForwardInput(Long version, BigDecimal amount, String note) {
}
