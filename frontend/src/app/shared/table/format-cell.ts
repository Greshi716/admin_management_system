import { CampusColumn } from './table-types';

const EMPTY = '—';

/**
 * Text shown in a cell. Dates use dd-MM-yyyy as in the agreement (R15); a timestamp also shows the time in the
 * browser's time zone, which on the school computer is India time.
 */
export function formatCell(value: unknown, type: CampusColumn['type'] = 'text'): string {
  if (value === null || value === undefined || value === '') {
    return EMPTY;
  }
  if (type === 'boolean' || typeof value === 'boolean') {
    return value === true || value === 'true' ? 'Yes' : 'No';
  }
  if (type === 'date' && typeof value === 'string') {
    return formatDate(value);
  }
  return String(value);
}

function formatDate(value: string): string {
  const dateOnly = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (dateOnly) {
    return `${dateOnly[3]}-${dateOnly[2]}-${dateOnly[1]}`;
  }
  const instant = new Date(value);
  if (Number.isNaN(instant.getTime())) {
    return value;
  }
  const two = (n: number) => String(n).padStart(2, '0');
  return (
    `${two(instant.getDate())}-${two(instant.getMonth() + 1)}-${instant.getFullYear()} ` +
    `${two(instant.getHours())}:${two(instant.getMinutes())}`
  );
}
