import { formatMoney, parseMoney } from './money';

describe('money box (F2 FR-011)', () => {
  it('reads plain amounts and ignores commas and spaces', () => {
    expect(parseMoney('28,000')).toBe(28000);
    expect(parseMoney(' 1 250.50 ')).toBe(1250.5);
    expect(parseMoney('0')).toBe(0);
  });

  it('keeps a minus sign so the server can refuse a negative amount', () => {
    expect(parseMoney('-5')).toBe(-5);
  });

  it('reads a blank box as null', () => {
    expect(parseMoney('')).toBeNull();
    expect(parseMoney('  ')).toBeNull();
  });

  it('refuses exponents, hex, signs, Infinity and words instead of reading them as numbers', () => {
    for (const text of ['1e3', '0x10', 'Infinity', '+5', 'abc', '1.', '.5', '1.2.3']) {
      expect(parseMoney(text)).toBeNaN();
    }
  });

  it('shows two decimals with Indian grouping', () => {
    expect(formatMoney(28000)).toBe('28,000.00');
    expect(formatMoney(null)).toBe('—');
  });
});
