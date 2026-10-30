import { expect, Page, test } from '@playwright/test';
import { addFeeRow, createFeeMasterData, FeeMasterData, finaliseYear } from './support/fees';
import { clearMasterData } from './support/master-data';
import { clearReceipts, getFeeCollection, recordReceipt } from './support/receipts';
import { createStudent, studentInput } from './support/students';
import { signInAsTestAdmin } from './support/test-data';
import { readSheetTyped } from './support/xlsx';

// Synthetic values only (constitution VI). End-to-end flow 2: the one controlled correction (Cheque not cleared), the
// register, the export and the audit log. Standard 1: 36,500.00, no registration fee.

interface Seed {
  master: FeeMasterData;
  studentId: number;
}

const now = new Date();
const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;

async function seed(page: Page): Promise<Seed> {
  const master = await createFeeMasterData(page, 1);
  await addFeeRow(page, master.yearId, { standardId: master.standards[0].id, amount: 36500 });
  const standard = master.standards[0];
  const student = await createStudent(
    page,
    studentInput(1, { yearId: master.yearId, standardId: standard.id, divisionId: standard.divisionId, categoryId: master.categories[0].id }),
  );
  await finaliseYear(page, master.yearId);
  return { master, studentId: student.id };
}

/** No edit, cancel or delete control on the screen (constitution II: a receipt is never changed). */
async function expectNoChangeControls(page: Page): Promise<void> {
  await expect(page.getByRole('button', { name: /(edit|cancel|delete|void) (the )?receipt/i })).toHaveCount(0);
  await expect(page.getByRole('link', { name: /(edit|cancel|delete|void) (the )?receipt/i })).toHaveCount(0);
}

test.describe('receipt corrections flow', () => {
  let data: Seed;

  test.beforeEach(async ({ page }) => {
    await signInAsTestAdmin(page);
    await clearReceipts(page);
    await clearMasterData(page);
    data = await seed(page);
  });

  test.afterEach(async ({ page }) => {
    await clearReceipts(page);
  });

  test('FR-009, FR-013, FR-019, FR-020, FR-021: mark a cheque not cleared, see the balance change, the register, the export and the audit log', async ({
    page,
  }) => {
    await recordReceipt(page, { studentId: data.studentId, receiptNo: '5000', receiptDate: today, feePaid: 10000 });
    const cheque = await recordReceipt(page, {
      studentId: data.studentId,
      receiptNo: '5001',
      receiptDate: today,
      feePaid: 5000,
      paymentMode: 'CHEQUE',
      chequeNo: '778899',
      chequeDate: today,
      bankName: 'Test Bank',
    });
    expect((await getFeeCollection(page, data.studentId)).balance['pending']).toBe(21500);

    await page.goto(`/finance/receipts/${cheque.serialNo}`);
    await expectNoChangeControls(page);
    await page.getByTestId('mark-not-cleared').click();
    await page.getByTestId('mark-confirm').click();
    await expect(page.getByTestId('mark-reason-error')).toBeVisible();
    expect(((await getFeeCollection(page, data.studentId)).receipts as { status: string }[]).map((r) => r.status)).not.toContain('CHEQUE_NOT_CLEARED');

    await page.getByLabel(/^Reason/).fill('Bounced, insufficient funds');
    await page.getByTestId('mark-confirm').click();
    await expect(page.getByTestId('receipt-status-tag')).toHaveText('Cheque not cleared');
    await expect(page.getByTestId('balance-effect')).toContainText('5,000.00 was added back');
    await expect(page.getByTestId('mark-not-cleared')).toHaveCount(0);
    await expectNoChangeControls(page);
    expect((await getFeeCollection(page, data.studentId)).balance['pending']).toBe(26500);

    await page.goto(`/students/${data.studentId}`);
    await expect(page.getByTestId('fee-paid')).toHaveText('10,000.00');
    await expect(page.getByTestId('fee-pending')).toHaveText('26,500.00');
    await expectNoChangeControls(page);

    await page.goto('/finance/receipts');
    await expect(page.getByTestId('receipt-status')).toHaveCount(2);
    expect(await page.getByTestId('receipt-status').allInnerTexts()).toEqual(['Cheque not cleared', 'Valid']);
    await expectNoChangeControls(page);

    const [download] = await Promise.all([page.waitForEvent('download'), page.getByTestId('export').click()]);
    const totals = readSheetTyped(await download.path(), 2);
    expect(totals[0].map((cell) => cell.value)).toEqual(['Date', 'Receipts', 'Cash', 'Cheque', 'Total']);
    expect(totals[1].slice(1).map((cell) => Number(cell.value))).toEqual([1, 10000, 0, 10000]);

    await page.goto('/audit-log');
    await expect(page.getByRole('heading', { name: 'Audit log' })).toBeVisible();
    await page.getByLabel('Area', { exact: true }).selectOption('Fees');
    const created = page.getByRole('row').filter({ hasText: `Receipt#${cheque.serialNo}` }).filter({ hasText: 'Create' });
    await expect(created).toContainText('5000.00');
    const edited = page.getByRole('row').filter({ hasText: `Receipt#${cheque.serialNo}` }).filter({ hasText: 'Edit' });
    await expect(edited).toContainText('Bounced, insufficient funds');
    await expect(edited).toContainText('5000.00');
    await expect(page.getByRole('row').filter({ hasText: 'Export' }).first()).toBeVisible();
  });
});
