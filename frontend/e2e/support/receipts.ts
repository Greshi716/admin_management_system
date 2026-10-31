import { Page } from '@playwright/test';
import { callApi } from './master-data';

/**
 * Helpers for the receipt specs, in the style of fees.ts. They call the same API as the screens (see
 * specs/004-fee-collection/contracts/openapi.yaml) and the test-profile endpoints. Synthetic values only
 * (constitution VI). Receipts cannot be deleted by the product, so a spec that clears students or master data calls
 * `clearReceipts` first. Set up the year, standards and a finalised structure with fees.ts (`createFeeMasterData`,
 * `addFeeRow`, `finaliseYear`) and the students with `seedClassRecords`.
 */

/** What a receipt request carries (ReceiptInput in the contract). Amounts are numbers with at most two decimals. */
export interface ReceiptInput {
  studentId: number;
  receiptType?: 'CURRENT_YEAR' | 'PREVIOUS_YEARS';
  receiptNo: string;
  receiptDate: string;
  registrationPaid?: number;
  feePaid?: number;
  previousYearId?: number;
  paymentMode?: 'CASH' | 'CHEQUE';
  chequeNo?: string;
  chequeDate?: string;
  bankName?: string;
}

/** A saved receipt as the API returns it (the fields the specs read). */
export interface Receipt {
  serialNo: number;
  receiptNo: string;
  receiptDate: string;
  total: number;
  totalWords: string;
  status: 'VALID' | 'CHEQUE_NOT_CLEARED' | 'VOIDED';
  paidUpTo: string | null;
  [key: string]: unknown;
}

/** The collection context of a student: the balance and the token the next save must send. */
export interface FeeCollection {
  balanceVersion: string;
  balance: Record<string, unknown>;
  [key: string]: unknown;
}

/** Reads the collection context of a student (the balance and its version). */
export async function getFeeCollection(page: Page, studentId: number): Promise<FeeCollection> {
  const result = await callApi(page, 'GET', `/api/students/${studentId}/fee-collection`);
  if (result.status !== 200) {
    throw new Error(`reading the fee collection of student ${studentId} failed with HTTP ${result.status}`);
  }
  return result.body;
}

/** Records a receipt through the API with the balance version the student has now, as the screen does. */
export async function recordReceipt(page: Page, input: ReceiptInput): Promise<Receipt> {
  const collection = await getFeeCollection(page, input.studentId);
  const result = await callApi(page, 'POST', '/api/receipts', {
    receiptType: 'CURRENT_YEAR',
    paymentMode: 'CASH',
    ...input,
    balanceVersion: collection.balanceVersion,
  });
  if (result.status !== 201) {
    throw new Error(`recording receipt ${input.receiptNo} failed with HTTP ${result.status}: ${JSON.stringify(result.body)}`);
  }
  return result.body;
}

/** Marks a cheque receipt "Cheque not cleared" with the balance version the student has now. */
export async function markChequeNotCleared(
  page: Page,
  studentId: number,
  serialNo: number,
  reason = 'Cheque bounced',
): Promise<Receipt> {
  const collection = await getFeeCollection(page, studentId);
  const result = await callApi(page, 'POST', `/api/receipts/${serialNo}/not-cleared`, {
    reason,
    balanceVersion: collection.balanceVersion,
  });
  if (result.status !== 200) {
    throw new Error(`marking receipt ${serialNo} failed with HTTP ${result.status}: ${JSON.stringify(result.body)}`);
  }
  return result.body;
}

/**
 * Seeds `count` cash receipts of `feePaid` each over the class records of the year (test-profile endpoint). The
 * receipts do not check what is due; use it for list and performance scenarios only.
 */
export async function seedReceipts(
  page: Page,
  options: { yearId: number; count: number; receiptDate: string; prefix?: string; feePaid?: number },
): Promise<number> {
  const query = new URLSearchParams({
    academicYearId: String(options.yearId),
    count: String(options.count),
    receiptDate: options.receiptDate,
    prefix: options.prefix ?? 'SEEDR',
    feePaid: (options.feePaid ?? 100).toFixed(2),
  });
  const result = await callApi(page, 'POST', `/api/test-support/seed-receipts?${query.toString()}`);
  if (result.status !== 200) {
    throw new Error(`seeding receipts failed with HTTP ${result.status}: ${JSON.stringify(result.body)}`);
  }
  return result.body.created;
}

/** Sets the amount a student owes from earlier years (test-profile endpoint). */
export async function setBroughtForward(page: Page, studentId: number, amount: number): Promise<void> {
  const result = await callApi(
    page,
    'POST',
    `/api/test-support/students/${studentId}/brought-forward?amount=${amount.toFixed(2)}`,
  );
  if (result.status !== 204) {
    throw new Error(`setting the brought-forward amount failed with HTTP ${result.status}`);
  }
}

/** Empties the receipt tables (test-profile endpoint); call before clearing fees, students or master data. */
export async function clearReceipts(page: Page): Promise<void> {
  await callApi(page, 'POST', '/api/test-support/clear-receipts');
}
