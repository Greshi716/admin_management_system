import { expect, Page, test } from '@playwright/test';
import { addFeeRow, createFeeMasterData, FeeMasterData, finaliseYear } from './support/fees';
import { clearMasterData } from './support/master-data';
import { clearReceipts, markChequeNotCleared, recordReceipt } from './support/receipts';
import { createStudent, studentInput } from './support/students';
import { signInAsTestAdmin } from './support/test-data';
import { readFirstSheetTyped, readSheetTyped } from './support/xlsx';

// Synthetic values only (constitution VI). Quickstart scenarios 17 and 18: the register, its filters, the day-wise
// totals panel and the Excel export. Standard 1: 36,500.00 with a registration fee of 500.00.
// Receipts: 3200 cash 500.00 + 10,000.00; 3201 cheque 5,000.00; 3202 cheque 3,000.00 marked not cleared.

interface Seed {
  master: FeeMasterData;
  studentId: number;
}

const now = new Date();
const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;

async function seed(page: Page): Promise<Seed> {
  const master = await createFeeMasterData(page, 1);
  await addFeeRow(page, master.yearId, { standardId: master.standards[0].id, amount: 36500, registrationFee: 500 });
  const standard = master.standards[0];
  const student = await createStudent(
    page,
    studentInput(3, { yearId: master.yearId, standardId: standard.id, divisionId: standard.divisionId, categoryId: master.categories[0].id }),
  );
  await finaliseYear(page, master.yearId);
  return { master, studentId: student.id };
}

const cheque = (studentId: number, receiptNo: string, feePaid: number) => ({
  studentId,
  receiptNo,
  receiptDate: today,
  feePaid,
  paymentMode: 'CHEQUE' as const,
  chequeNo: `CQ${receiptNo}`,
  chequeDate: today,
  bankName: 'Test Bank',
});

const rowTexts = (page: Page, testId: string) => page.getByTestId(testId).allInnerTexts();

test.describe('receipt register', () => {
  let data: Seed;

  test.beforeEach(async ({ page }) => {
    await signInAsTestAdmin(page);
    await clearReceipts(page);
    await clearMasterData(page);
    data = await seed(page);
    await recordReceipt(page, { studentId: data.studentId, receiptNo: '3200', receiptDate: today, registrationPaid: 500, feePaid: 10000 });
    await recordReceipt(page, cheque(data.studentId, '3201', 5000));
    const bounced = await recordReceipt(page, cheque(data.studentId, '3202', 3000));
    await markChequeNotCleared(page, data.studentId, bounced.serialNo);
  });

  test.afterEach(async ({ page }) => {
    await clearReceipts(page);
  });

  test('FR-019: Fee collection has a Receipts view that lists the receipts newest first with amounts and status tags (scenario 17)', async ({
    page,
  }) => {
    await page.goto('/finance/fee-collection');
    await page.getByTestId('view-receipts').click();
    await expect(page).toHaveURL(/\/finance\/receipts$/);

    await expect(page.getByRole('heading', { name: 'Receipts' })).toBeVisible();
    await expect(page.getByTestId('receipt-status')).toHaveCount(3);
    const rows = page.locator('tbody tr');
    await expect(rows.nth(0)).toContainText('3202');
    await expect(rows.nth(1)).toContainText('3201');
    await expect(rows.nth(2)).toContainText('3200');
    expect(await rowTexts(page, 'receipt-status')).toEqual(['Cheque not cleared', 'Valid', 'Valid']);
    expect(await rowTexts(page, 'amount-total')).toEqual(['3,000.00', '5,000.00', '10,500.00']);
    expect(await rowTexts(page, 'amount-registrationPaid')).toEqual(['0.00', '0.00', '500.00']);
    await expect(page.getByRole('columnheader', { name: /paid up to/i })).toHaveCount(0);
  });

  test('FR-019: the Mode and Status filters and the search narrow the list, and a row opens its receipt (scenario 17)', async ({ page }) => {
    await page.goto('/finance/receipts');
    await expect(page.getByTestId('receipt-status')).toHaveCount(3);

    await page.getByRole('button', { name: 'Filter Mode' }).click();
    await page.getByRole('group', { name: 'Filter Mode' }).locator('select').selectOption({ label: 'Cheque' });
    await expect(page.getByTestId('receipt-status')).toHaveCount(2);

    await page.getByRole('button', { name: 'Filter Status' }).click();
    await page.getByRole('group', { name: 'Filter Status' }).locator('select').selectOption({ label: 'Cheque not cleared' });
    await expect(page.getByTestId('receipt-status')).toHaveCount(1);
    await expect(page.locator('tbody tr').first()).toContainText('3202');

    await page.getByRole('group', { name: 'Filter Status' }).locator('select').selectOption({ label: 'All' });
    await page.getByRole('button', { name: 'Filter Mode' }).click();
    await page.getByRole('group', { name: 'Filter Mode' }).locator('select').selectOption({ label: 'All' });
    await page.getByRole('searchbox', { name: 'Search this list' }).fill('3200');
    await expect(page.getByTestId('receipt-status')).toHaveCount(1);

    await page.getByTestId('open-receipt').click();
    await expect(page).toHaveURL(/\/finance\/receipts\/\d+$/);
    await expect(page.getByRole('heading', { name: 'Receipt 3200' })).toBeVisible();
  });

  test('FR-020: the day-wise totals panel adds up the valid receipts of the filters and leaves out the cheque not cleared (scenario 17)', async ({
    page,
  }) => {
    await page.goto('/finance/receipts');

    await expect(page.getByTestId('day-total-row')).toHaveCount(1);
    await expect(page.getByTestId('day-count')).toHaveText('2');
    await expect(page.getByTestId('day-cash')).toHaveText('10,500.00');
    await expect(page.getByTestId('day-cheque')).toHaveText('5,000.00');
    await expect(page.getByTestId('day-total')).toHaveText('15,500.00');

    await page.getByRole('button', { name: 'Filter Mode' }).click();
    await page.getByRole('group', { name: 'Filter Mode' }).locator('select').selectOption({ label: 'Cash' });
    await expect(page.getByTestId('day-cheque')).toHaveText('0.00');
    await expect(page.getByTestId('day-total')).toHaveText('10,500.00');
  });

  test('FR-020, FR-021: the Excel file has the same rows, numeric amounts and a Day-wise totals sheet that equals the valid receipts (scenario 18)', async ({
    page,
  }) => {
    await page.goto('/finance/receipts');
    await expect(page.getByTestId('receipt-status')).toHaveCount(3);

    const [download] = await Promise.all([page.waitForEvent('download'), page.getByTestId('export').click()]);

    expect(download.suggestedFilename()).toMatch(/^receipts-\d{4}-\d{2}-\d{2}\.xlsx$/);
    const file = await download.path();
    const sheet = readFirstSheetTyped(file);
    const headers = sheet[0].map((cell) => cell.value);
    for (const header of ['Receipt no.', 'Date', 'Student', 'Mode', 'Registration fee', 'Year fee', "Previous years' fee", 'Total', 'Status', 'Cheque no.', 'Bank']) {
      expect(headers).toContain(header);
    }
    expect(headers).not.toContain('Paid up to');
    const at = (header: string) => headers.indexOf(header);
    const body = sheet.slice(1);
    expect(body.map((row) => row[at('Receipt no.')].value)).toEqual(['3202', '3201', '3200']);
    expect(body.map((row) => row[at('Status')].value)).toEqual(['Cheque not cleared', 'Valid', 'Valid']);
    for (const header of ['Registration fee', 'Year fee', "Previous years' fee", 'Total']) {
      for (const row of body) {
        expect(row[at(header)].kind).toBe('number');
      }
    }
    expect(Number(body[2][at('Total')].value)).toBe(10500);
    expect(Number(body[2][at('Registration fee')].value)).toBe(500);
    expect(Number(body[1][at('Year fee')].value)).toBe(5000);

    const totals = readSheetTyped(file, 2);
    expect(totals[0].map((cell) => cell.value)).toEqual(['Date', 'Receipts', 'Cash', 'Cheque', 'Total']);
    expect(totals.length).toBe(2);
    expect(totals[1].slice(1).map((cell) => cell.kind)).toEqual(['number', 'number', 'number', 'number']);
    expect(totals[1].slice(1).map((cell) => Number(cell.value))).toEqual([2, 10500, 5000, 15500]);
  });
});
