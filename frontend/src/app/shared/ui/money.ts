const FORMAT = new Intl.NumberFormat('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/**
 * An amount for display: Indian digit grouping and always two decimals (28000 shows as 28,000.00). The browser only
 * formats what the server sent; it never adds or rounds amounts (F2 FR-011).
 */
export function formatMoney(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : FORMAT.format(value);
}

/**
 * Reads a money box the admin typed: commas and spaces are ignored. A blank box is null, text that is not plain digits
 * with an optional decimal part (for example 1e3, 0x10 or Infinity) is NaN. A minus sign is kept so the server can say
 * the amount cannot be negative. Whether the value is allowed (not negative, two decimals at most) is the server's
 * decision (F2 FR-011).
 */
export function parseMoney(text: string): number | null {
  const cleaned = text.replace(/[,\s]/g, '');
  if (cleaned === '') {
    return null;
  }
  return /^-?\d+(\.\d+)?$/.test(cleaned) ? Number(cleaned) : NaN;
}
