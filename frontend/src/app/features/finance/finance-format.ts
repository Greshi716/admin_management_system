import { PaidUpTo, ReceiptStatus } from './receipts.service';

const DATE = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });

/** A date from the server (yyyy-mm-dd) for display, for example 9 Jul 2026. Empty for a missing date. */
export function formatDate(iso: string | null | undefined): string {
  if (!iso) {
    return '—';
  }
  const [year, month, day] = iso.split('-').map(Number);
  return DATE.format(new Date(year, month - 1, day));
}

/** Today in the browser's time zone as yyyy-mm-dd, the form's default receipt date. The server decides what is allowed. */
export function todayIso(): string {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}

/** "Fees paid up to" in words: Nothing paid, a date, or Fully paid for the year. */
export function paidUpToText(paidUpTo: PaidUpTo | null | undefined): string {
  switch (paidUpTo?.state) {
    case 'DATE':
      return formatDate(paidUpTo.date);
    case 'FULL':
      return 'Fully paid for the year';
    default:
      return 'Nothing paid';
  }
}

const STATUS_TEXT: Record<ReceiptStatus, string> = {
  VALID: 'Valid',
  CHEQUE_NOT_CLEARED: 'Cheque not cleared',
  VOIDED: 'Voided by correction',
};

const STATUS_CLASS: Record<ReceiptStatus, string> = {
  VALID: 'bg-badge-violet-bg text-badge-violet-text',
  CHEQUE_NOT_CLEARED: 'bg-warning-bg text-warning-text',
  VOIDED: 'bg-error-bg text-error-text',
};

/** The words of a receipt status (the server sends the code). */
export function receiptStatusText(status: ReceiptStatus): string {
  return STATUS_TEXT[status];
}

/** The tag classes of a receipt status: Valid violet, Cheque not cleared warning, Voided by correction error. */
export function receiptStatusClass(status: ReceiptStatus): string {
  return STATUS_CLASS[status];
}
