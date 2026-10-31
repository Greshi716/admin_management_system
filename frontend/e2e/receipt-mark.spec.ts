import { expect, Page, test } from '@playwright/test';
import { addFeeRow, createFeeMasterData, FeeMasterData, finaliseYear } from './support/fees';
import { callApi, clearMasterData } from './support/master-data';
import { clearReceipts, getFeeCollection, recordReceipt } from './support/receipts';
import { createStudent, studentInput } from './support/students';
import { signInAsTestAdmin } from './support/test-data';

// Synthetic values only (constitution VI). Quickstart scenarios 12 and 13 through the receipt view.
// Standard 1: 36,500.00, no registration fee. Student B pays 5,000.00 by cheque (receipt 3300).

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
    studentInput(2, { yearId: master.yearId, standardId: standard.id, divisionId: standard.divisionId, categoryId: master.categories[0].id }),
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

test.describe('cheque not cleared', () => {
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

  test('FR-013, FR-009: an empty reason is refused, a reason marks the cheque, the amount is added back and the action is gone (scenario 12)', async ({
    page,
  }) => {
    const receipt = await recordReceipt(page, cheque(data.studentId, '3300', 5000));
    expect((await getFeeCollection(page, data.studentId)).balance['pending']).toBe(31500);

    await page.goto(`/finance/receipts/${receipt.serialNo}`);
    await expect(page.getByTestId('receipt-status-tag')).toHaveText('Valid');
    await page.getByTestId('mark-not-cleared').click();
    await expect(page.getByTestId('mark-explain')).toContainText('5,000.00 will be added back');

    await page.getByTestId('mark-confirm').click();
    await expect(page.getByTestId('mark-reason-error')).toBeVisible();
    expect(((await getFeeCollection(page, data.studentId)).receipts[0] as { status: string }).status).toBe('VALID');

    await page.getByLabel(/^Reason/).fill('Bounced, insufficient funds');
    await page.getByTestId('mark-confirm').click();

    await expect(page.getByTestId('receipt-status-tag')).toHaveText('Cheque not cleared');
    await expect(page.getByTestId('receipt-status-reason')).toContainText('Bounced, insufficient funds');
    await expect(page.getByTestId('balance-effect')).toContainText('5,000.00 was added back');
    await expect(page.getByTestId('mark-not-cleared')).toHaveCount(0);
    expect((await getFeeCollection(page, data.studentId)).balance['pending']).toBe(36500);

    const audit = await callApi(page, 'POST', '/api/audit-log/search', { first: 0, rows: 25, filters: { area: { value: 'Fees', matchMode: 'equals' } } });
    expect(JSON.stringify(audit.body)).toContain('Bounced, insufficient funds');
  });

  test('FR-013: a Cash receipt has no such action (scenario 12)', async ({ page }) => {
    const receipt = await recordReceipt(page, { studentId: data.studentId, receiptNo: '3310', receiptDate: today, feePaid: 100 });

    await page.goto(`/finance/receipts/${receipt.serialNo}`);
    await expect(page.getByTestId('receipt-status-tag')).toHaveText('Valid');
    await expect(page.getByTestId('mark-not-cleared')).toHaveCount(0);
  });

  test('FR-001, FR-013: the money is paid again with a new receipt, and the number 3300 stays taken (scenario 13)', async ({ page }) => {
    const receipt = await recordReceipt(page, cheque(data.studentId, '3300', 5000));
    await page.goto(`/finance/receipts/${receipt.serialNo}`);
    await page.getByTestId('mark-not-cleared').click();
    await page.getByLabel(/^Reason/).fill('Bounced');
    await page.getByTestId('mark-confirm').click();
    await expect(page.getByTestId('receipt-status-tag')).toHaveText('Cheque not cleared');

    await recordReceipt(page, { studentId: data.studentId, receiptNo: '3301', receiptDate: today, feePaid: 5000 });
    const collection = await getFeeCollection(page, data.studentId);
    expect(collection.balance['paid']).toBe(5000);
    expect(collection.receipts).toHaveLength(2);

    const again = await callApi(page, 'POST', '/api/receipts', {
      ...cheque(data.studentId, '3300', 100),
      paymentMode: 'CASH',
      chequeNo: null,
      chequeDate: null,
      bankName: null,
      receiptType: 'CURRENT_YEAR',
      balanceVersion: collection.balanceVersion,
    });
    expect(again.status).toBe(409);
    expect(again.body.code).toBe('DUPLICATE_RECEIPT_NO');
  });

  test('FR-016: if the balance changed after the receipt was opened, the pop-up asks for Refresh and nothing is marked', async ({ page }) => {
    const receipt = await recordReceipt(page, cheque(data.studentId, '3320', 1000));
    await page.goto(`/finance/receipts/${receipt.serialNo}`);
    await expect(page.getByTestId('mark-not-cleared')).toBeVisible();
    await recordReceipt(page, { studentId: data.studentId, receiptNo: '3321', receiptDate: today, feePaid: 10 });

    await page.getByTestId('mark-not-cleared').click();
    await page.getByLabel(/^Reason/).fill('Bounced');
    await page.getByTestId('mark-confirm').click();
    await expect(page.getByTestId('balance-changed-text')).toBeVisible();
    await expect(page.getByTestId('receipt-status-tag')).toHaveText('Valid');

    await page.getByRole('button', { name: 'Refresh' }).click();
    await page.getByTestId('mark-not-cleared').click();
    await page.getByLabel(/^Reason/).fill('Bounced');
    await page.getByTestId('mark-confirm').click();
    await expect(page.getByTestId('receipt-status-tag')).toHaveText('Cheque not cleared');
  });
});
