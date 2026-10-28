const FORMAT = new Intl.NumberFormat('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/**
 * An amount for display: Indian digit grouping and always two decimals (28000 shows as 28,000.00). The browser only
 * formats what the server sent; it never adds or rounds amounts (F2 FR-011).
 */
export function formatMoney(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : FORMAT.format(value);
}
