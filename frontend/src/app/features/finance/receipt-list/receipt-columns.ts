import { CampusColumn } from '../../../shared/table/table-types';
import { FilterOptions } from '../../students/students.service';

/** The words of the status filter and the Status column, as the server writes them. */
export const REGISTER_STATUSES = ['Valid', 'Cheque not cleared', 'Voided by correction'] as const;

export const REGISTER_MODES = ['Cash', 'Cheque'] as const;

const STATUS_CLASS: Record<string, string> = {
  Valid: 'bg-badge-violet-bg text-badge-violet-text',
  'Cheque not cleared': 'bg-warning-bg text-warning-text',
  'Voided by correction': 'bg-error-bg text-error-text',
};

/** The tag classes of a status word of the register. */
export function registerStatusClass(status: unknown): string {
  return STATUS_CLASS[String(status)] ?? '';
}

/** The money columns of the register: drawn with two decimals from the amounts the server sent. */
export const MONEY_FIELDS = ['registrationPaid', 'yearFee', 'previousYearsFee', 'total'];

/**
 * The columns of the receipt register (FR-019). The server declares the same columns and exports all of them,
 * including the three cheque columns that are not shown on screen. The date column filters by a range; Mode, Status
 * and Std / Div are dropdowns. There is no Paid up to column: a receipt carries none.
 */
export function registerColumns(options: FilterOptions | null): CampusColumn[] {
  return [
    { field: 'serialNo', header: 'Serial no.', type: 'number', hidden: true },
    { field: 'studentId', header: 'Student id', type: 'number', hidden: true },
    { field: 'receiptNo', header: 'Receipt no.' },
    { field: 'receiptDate', header: 'Date', type: 'date' },
    { field: 'student', header: 'Student' },
    { field: 'grNo', header: 'GR No.', filterable: false },
    {
      field: 'stdDiv',
      header: 'Std / Div',
      dropdowns: [
        {
          field: 'standardId',
          label: 'Standard',
          options: (options?.standards ?? []).map((standard) => ({ value: standard.id, label: standard.name })),
        },
      ],
    },
    { field: 'type', header: 'Type', filterable: false },
    {
      field: 'mode',
      header: 'Mode',
      dropdowns: [{ field: 'mode', label: 'Mode', options: REGISTER_MODES.map((mode) => ({ value: mode, label: mode })) }],
    },
    { field: 'registrationPaid', header: 'Registration fee', type: 'number', custom: true, filterable: false },
    { field: 'yearFee', header: 'Year fee', type: 'number', custom: true, filterable: false },
    { field: 'previousYearsFee', header: "Previous years' fee", type: 'number', custom: true, filterable: false },
    { field: 'total', header: 'Total', type: 'number', custom: true, filterable: false },
    {
      field: 'status',
      header: 'Status',
      custom: true,
      dropdowns: [
        { field: 'status', label: 'Status', options: REGISTER_STATUSES.map((status) => ({ value: status, label: status })) },
      ],
    },
    { field: 'chequeNo', header: 'Cheque no.', hidden: true },
    { field: 'chequeDate', header: 'Cheque date', type: 'date', hidden: true },
    { field: 'bankName', header: 'Bank', hidden: true },
  ];
}
