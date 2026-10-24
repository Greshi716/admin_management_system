import { formatCell } from './format-cell';

// FR-027
describe('formatCell', () => {
  it('shows a dash for missing values', () => {
    expect(formatCell(null)).toBe('—');
    expect(formatCell(undefined, 'date')).toBe('—');
    expect(formatCell('')).toBe('—');
  });

  it('shows dates as dd-MM-yyyy', () => {
    expect(formatCell('2026-04-01', 'date')).toBe('01-04-2026');
  });

  it('shows a timestamp with the time', () => {
    const local = new Date(2026, 3, 1, 9, 5);
    expect(formatCell(local.toISOString(), 'date')).toBe('01-04-2026 09:05');
  });

  it('leaves an unreadable date as it came', () => {
    expect(formatCell('not a date', 'date')).toBe('not a date');
  });

  it('shows booleans as Yes and No', () => {
    expect(formatCell(true, 'boolean')).toBe('Yes');
    expect(formatCell(false, 'boolean')).toBe('No');
    expect(formatCell(false)).toBe('No');
  });

  it('shows numbers and text as they are, including zero', () => {
    expect(formatCell(0, 'number')).toBe('0');
    expect(formatCell(42, 'number')).toBe('42');
    expect(formatCell('2026-27')).toBe('2026-27');
  });
});
