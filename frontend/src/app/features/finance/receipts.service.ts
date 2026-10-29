import { HttpClient, HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiProblem, problemOf } from '../../core/http/api-problem';
import { TablePage, TableRequest } from '../../shared/table/table-types';

export type ReceiptType = 'CURRENT_YEAR' | 'PREVIOUS_YEARS';
export type PaymentMode = 'CASH' | 'CHEQUE';
export type ReceiptStatus = 'VALID' | 'CHEQUE_NOT_CLEARED' | 'VOIDED';
export type CurrentYearState = 'FEE_SET' | 'FEE_NOT_SET' | 'FEE_NOT_FINALISED' | 'NO_CLASS_RECORD' | 'NO_CURRENT_YEAR';

export const BALANCE_CHANGED = 'BALANCE_CHANGED';
export const OVERPAYMENT = 'OVERPAYMENT';
export const BROUGHT_FORWARD_PENDING = 'BROUGHT_FORWARD_PENDING';
export const DUPLICATE_RECEIPT_NO = 'DUPLICATE_RECEIPT_NO';

/** "Fees paid up to" (contracts/openapi.yaml `PaidUpTo`). */
export interface PaidUpTo {
  state: 'NOTHING' | 'DATE' | 'FULL';
  date: string | null;
}

/** The balance of a student, computed by the server from the valid receipts. The browser never adds amounts. */
export interface Balance {
  dueDate: string | null;
  yearFee: number;
  registrationFee: number;
  broughtForward: number;
  totalFee: number;
  paid: number;
  pending: number;
  yearFeeDue: number;
  registrationDue: number;
  earlierYearsPending: number;
  excessPaid: number;
  paidUpTo: PaidUpTo;
}

export interface ReceiptSummary {
  serialNo: number;
  receiptNo: string;
  receiptDate: string;
  receiptType: ReceiptType;
  paymentMode: PaymentMode;
  total: number;
  status: ReceiptStatus;
}

export interface FeeCollection {
  student: { id: number; name: string; grNo: string; status: string; stdDiv: string };
  currentYear: { id: number; name: string; startDate: string; endDate: string; state: CurrentYearState } | null;
  canPayCurrentYear: boolean;
  canPayPreviousYears: boolean;
  chargesRegistrationFee: boolean;
  earlierYears: { id: number; name: string }[];
  balance: Balance;
  balanceVersion: string;
  receipts: ReceiptSummary[];
}

/** What the form sends. Amounts are numbers typed by the admin; the server checks them and never rounds them. */
export interface ReceiptInput {
  studentId: number;
  receiptType: ReceiptType;
  receiptNo: string;
  receiptDate: string;
  registrationPaid?: number | null;
  feePaid?: number | null;
  previousYearId?: number | null;
  paymentMode: PaymentMode;
  chequeNo?: string | null;
  chequeDate?: string | null;
  bankName?: string | null;
  balanceVersion: string;
}

export interface ReceiptPreview {
  total: number;
  totalWords: string;
  balanceAfter: Balance;
}

export interface Receipt extends ReceiptSummary {
  studentId: number;
  balanceVersion: string;
  academicYear: string;
  studentName: string;
  grNo: string;
  standardName: string | null;
  divisionName: string | null;
  registrationPaid: number;
  feePaid: number;
  totalWords: string;
  chequeNo: string | null;
  chequeDate: string | null;
  bankName: string | null;
  statusReason: string | null;
  markedAt: string | null;
  school: { name: string; address: string | null; phone: string | null };
}

export interface DayTotal {
  date: string;
  count: number;
  cash: number;
  cheque: number;
  total: number;
}

/** The balance changed after the screen loaded; nothing was saved. `current` is the fresh context. */
export class BalanceChanged {
  constructor(readonly current: FeeCollection) {}
}

/** An amount is above what is still due; `due` is what is still due for that row. */
export class Overpayment {
  constructor(
    readonly field: string,
    readonly due: number,
    readonly message: string,
  ) {}
}

/** Earlier years' pending must be paid first. */
export class BroughtForwardPending {
  constructor(
    readonly pending: number,
    readonly message: string,
  ) {}
}

/** The receipt number is held by another receipt that is not voided. */
export class DuplicateReceiptNo {
  constructor(readonly message: string) {}
}

/** Any other refusal that depends on the data (FEE_NOT_SET, NOT_A_CHEQUE, ALREADY_MARKED, ...). */
export class ReceiptRefusal {
  constructor(
    readonly code: string,
    readonly message: string,
  ) {}
}

export type ReceiptConflict = BalanceChanged | Overpayment | BroughtForwardPending | DuplicateReceiptNo | ReceiptRefusal;

/** The typed meaning of a failed receipt call; null when it is not a 409 (for example a 422 with field errors). */
export function receiptConflictOf(error: unknown): ReceiptConflict | null {
  if (!(error instanceof HttpErrorResponse) || error.status !== 409) {
    return null;
  }
  const problem: ApiProblem | null = problemOf(error.error);
  const message = problem?.detail ?? problem?.title ?? 'The receipt was not saved.';
  const current = problem?.current as Record<string, unknown> | undefined;
  switch (problem?.code) {
    case BALANCE_CHANGED:
      return new BalanceChanged(problem.current as FeeCollection);
    case OVERPAYMENT:
      return new Overpayment(
        problem.fieldErrors?.[0]?.field ?? 'feePaid',
        typeof current?.['due'] === 'number' ? current['due'] : 0,
        message,
      );
    case BROUGHT_FORWARD_PENDING:
      return new BroughtForwardPending(typeof current?.['pending'] === 'number' ? current['pending'] : 0, message);
    case DUPLICATE_RECEIPT_NO:
      return new DuplicateReceiptNo(message);
    case undefined:
      // Two saves of one number at the same moment are refused by the database constraint: no code, the field only.
      return problem?.fieldErrors?.[0]?.field === 'receiptNo' ? new DuplicateReceiptNo(message) : null;
    default:
      return new ReceiptRefusal(problem?.code ?? 'CONFLICT', message);
  }
}

/** Calls of the Collect fee screen, the receipt view and the register (F3). */
@Injectable({ providedIn: 'root' })
export class ReceiptsService {
  private readonly http = inject(HttpClient);

  feeCollection(studentId: number): Observable<FeeCollection> {
    return this.http.get<FeeCollection>(`/api/students/${studentId}/fee-collection`);
  }

  preview(studentId: number, input: Partial<ReceiptInput>): Observable<ReceiptPreview> {
    return this.http.post<ReceiptPreview>(`/api/students/${studentId}/fee-collection/preview`, input);
  }

  create(input: ReceiptInput): Observable<Receipt> {
    return this.http.post<Receipt>('/api/receipts', input);
  }

  get(serialNo: number): Observable<Receipt> {
    return this.http.get<Receipt>(`/api/receipts/${serialNo}`);
  }

  markNotCleared(serialNo: number, reason: string, balanceVersion: string): Observable<Receipt> {
    return this.http.post<Receipt>(`/api/receipts/${serialNo}/not-cleared`, { reason, balanceVersion });
  }

  search(request: TableRequest): Observable<TablePage> {
    return this.http.post<TablePage>('/api/receipts/search', request);
  }

  dayTotals(request: TableRequest): Observable<DayTotal[]> {
    return this.http.post<DayTotal[]>('/api/receipts/day-totals', request);
  }

  exportRegister(request: TableRequest): Observable<HttpResponse<Blob>> {
    return this.http.post('/api/receipts/export', request, { observe: 'response', responseType: 'blob' });
  }
}
