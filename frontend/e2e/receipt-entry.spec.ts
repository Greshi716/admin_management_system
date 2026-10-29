import { expect, Page, test } from '@playwright/test';
import { addFeeRow, createFeeMasterData, FeeMasterData, finaliseYear } from './support/fees';
import { callApi, clearMasterData } from './support/master-data';
import { clearReceipts, getFeeCollection, recordReceipt, setBroughtForward } from './support/receipts';
import { createStudent, studentInput } from './support/students';
import { signInAsTestAdmin } from './support/test-data';

// Synthetic values only (constitution VI). Quickstart scenarios 1 to 5, 9, 14, 15 and the layout checks of section 4.
// Standard 1: 36,500.00 with registration fee 500.00. Standard 2: 18,250.00, no registration fee. Standard 3: no fee row.

interface Seed {
  master: FeeMasterData;
  /** Student ids by their number n (GR No. E2E-n). */
  ids: Map<number, number>;
}

const category = (master: FeeMasterData) => master.categories[0].id;

async function register(page: Page, master: FeeMasterData, n: number, standardIndex: number, ids: Map<number, number>) {
  const standard = master.standards[standardIndex];
  const student = await createStudent(
    page,
    studentInput(n, { yearId: master.yearId, standardId: standard.id, divisionId: standard.divisionId, categoryId: category(master) }),
  );
  ids.set(n, student.id);
}

async function seed(page: Page, finalise = true): Promise<Seed> {
  const master = await createFeeMasterData(page, 3);
  await addFeeRow(page, master.yearId, { standardId: master.standards[0].id, amount: 36500, registrationFee: 500, dueDate: '2030-07-15' });
  await addFeeRow(page, master.yearId, { standardId: master.standards[1].id, amount: 18250 });
  const ids = new Map<number, number>();
  await register(page, master, 1, 0, ids); // A
  await register(page, master, 2, 0, ids); // B
  await register(page, master, 3, 1, ids); // C, no registration fee
  await register(page, master, 4, 1, ids); // D, earlier years pending
  await register(page, master, 5, 2, ids); // E, no fee row
  if (finalise) {
    await finaliseYear(page, master.yearId);
  }
  return { master, ids };
}

const today = new Date();
const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

async function openForm(page: Page, studentId: number): Promise<void> {
  await page.goto(`/finance/fee-collection?studentId=${studentId}`);
  await expect(page.getByTestId('strip')).toBeVisible();
}

async function saveAndConfirm(page: Page): Promise<void> {
  await page.getByTestId('save').click();
  const confirm = page.getByRole('button', { name: 'Confirm and save' });
  await confirm.waitFor({ state: 'visible', timeout: 3000 }).catch(() => undefined);
  if (await confirm.isVisible()) {
    await confirm.click();
  }
}

test.describe('collect fee', () => {
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

  const id = (n: number) => data.ids.get(n)!;

  test('FR-001, FR-003, FR-008, FR-010, FR-011: enter a receipt, check it in the dialog, Cancel keeps the values, Confirm saves (scenario 1)', async ({ page }) => {
    await openForm(page, id(1));
    await page.getByLabel('Receipt no. (from the book)').fill('3100');
    await page.getByLabel('Registration fee').fill('500');
    await page.getByLabel('Year fee').fill('10000');

    await expect(page.getByTestId('live-total')).toHaveText('10,500.00');
    await expect(page.getByTestId('live-words')).toContainText('Ten Thousand Five Hundred');

    await page.getByTestId('save').click();
    await expect(page.getByTestId('confirm-receipt-no')).toHaveText('3100');
    await expect(page.getByTestId('confirm-total')).toHaveText('10,500.00');
    await expect(page.getByTestId('confirm-details')).toContainText('Registration fee');
    await expect(page.getByTestId('confirm-details')).toContainText('10,000.00');
    await expect(page.getByTestId('confirm-student')).toContainText('E2E-1');

    await page.getByRole('button', { name: 'Cancel' }).click();
    await expect(page.getByTestId('confirm-details')).toHaveCount(0);
    await expect(page.getByLabel('Receipt no. (from the book)')).toHaveValue('3100');
    await expect(page.getByLabel('Year fee')).toHaveValue('10000');
    expect((await getFeeCollection(page, id(1))).receipts).toHaveLength(0);

    await page.getByTestId('save').click();
    await page.getByRole('button', { name: 'Confirm and save' }).click();
    await expect(page).toHaveURL(/\/finance\/receipts\/\d+$/);
    await expect(page.getByTestId('receipt-total')).toHaveText('10,500.00');
    await expect(page.getByTestId('serial-no')).toHaveText(/\d+/);
    await expect(page.getByTestId('receipt-status')).toContainText('Valid');
  });

  test('FR-001: a used receipt number is refused beside the field, "03100" is a different number (scenario 2)', async ({ page }) => {
    await recordReceipt(page, { studentId: id(2), receiptNo: '3100', receiptDate: iso(today), feePaid: 100 });

    await openForm(page, id(1));
    await page.getByLabel('Receipt no. (from the book)').fill('3100');
    await page.getByLabel('Year fee').fill('100');
    await saveAndConfirm(page);
    await expect(page.getByRole('alert').filter({ hasText: /already used/ }).first()).toBeVisible();
    await expect(page).toHaveURL(/fee-collection/);
    expect((await getFeeCollection(page, id(1))).receipts).toHaveLength(0);

    await page.getByLabel('Receipt no. (from the book)').fill('03100');
    await saveAndConfirm(page);
    await expect(page).toHaveURL(/\/finance\/receipts\/\d+$/);
  });

  test('FR-002, FR-003, FR-023: tomorrow, both amounts zero, three decimals and a minus sign are each refused (scenario 3)', async ({ page }) => {
    await openForm(page, id(1));
    await page.getByLabel('Receipt no. (from the book)').fill('3110');

    const tomorrow = new Date(today);
    tomorrow.setDate(today.getDate() + 1);
    await page.getByLabel('Date').fill(iso(tomorrow));
    await page.getByLabel('Year fee').fill('100');
    await saveAndConfirm(page);
    await expect(page.getByRole('alert').filter({ hasText: /future|today/i }).first()).toBeVisible();
    await page.getByLabel('Date').fill(iso(today));

    await page.getByLabel('Registration fee').fill('0');
    await page.getByLabel('Year fee').fill('0');
    await saveAndConfirm(page);
    await expect(page.getByRole('alert').first()).toBeVisible();

    await page.getByLabel('Year fee').fill('100.123');
    await expect(page.getByRole('alert').filter({ hasText: /two decimal|decimal/i }).first()).toBeVisible();

    await page.getByLabel('Year fee').fill('-1');
    await expect(page.getByRole('alert').filter({ hasText: /negative|less than|zero/i }).first()).toBeVisible();
    expect((await getFeeCollection(page, id(1))).receipts).toHaveLength(0);
  });

  test('FR-006: a Cheque needs its number, date and bank; Cash shows none of them (scenario 4)', async ({ page }) => {
    await openForm(page, id(1));
    await expect(page.getByLabel('Cheque no.')).toHaveCount(0);
    await page.getByLabel('Payment mode').selectOption('CHEQUE');
    await expect(page.getByLabel('Cheque no.')).toBeVisible();

    await page.getByLabel('Receipt no. (from the book)').fill('3120');
    await page.getByLabel('Year fee').fill('100');
    await saveAndConfirm(page);
    await expect(page.getByRole('alert').first()).toBeVisible();
    expect((await getFeeCollection(page, id(1))).receipts).toHaveLength(0);

    await page.getByLabel('Cheque no.').fill('778899');
    await page.getByLabel('Cheque date').fill(iso(today));
    await page.getByLabel('Bank name').fill('Test Bank');
    await saveAndConfirm(page);
    await expect(page).toHaveURL(/\/finance\/receipts\/\d+$/);

    await openForm(page, id(1));
    await page.getByLabel('Payment mode').selectOption('CASH');
    await expect(page.getByLabel('Cheque no.')).toHaveCount(0);
  });

  test('FR-005: an amount above what is still due shows the amount still due (scenario 5)', async ({ page }) => {
    await recordReceipt(page, { studentId: id(1), receiptNo: '3130', receiptDate: iso(today), registrationPaid: 500, feePaid: 10000 });

    await openForm(page, id(1));
    await expect(page.getByTestId('fee-due')).toHaveText(/26,500\.00/);
    await page.getByLabel('Year fee').fill('30000');
    await expect(page.getByRole('alert').filter({ hasText: 'Only 26500.00 is still due for the year fee.' })).toBeVisible();
    await page.getByLabel('Receipt no. (from the book)').fill('3131');
    await saveAndConfirm(page);
    await expect(page).toHaveURL(/fee-collection/);
    expect((await getFeeCollection(page, id(1))).receipts).toHaveLength(1);
  });

  test('FR-005, FR-004: earlier years first - the previous-years type is offered first and the current year is closed (scenario 9)', async ({ page }) => {
    await setBroughtForward(page, id(4), 3000);

    await openForm(page, id(4));
    await expect(page.getByTestId('earlier-first')).toContainText('3,000.00');
    await expect(page.getByLabel('Receipt type')).toHaveValue('PREVIOUS_YEARS');
    await expect(page.getByLabel('Registration fee')).toHaveCount(0);
    await expect(page.getByRole('option', { name: 'Current year' })).toBeDisabled();
  });

  test('FR-007: fee not set and fee not finalised are explained and give no form; a Left student can pay (scenario 14)', async ({ page }) => {
    await openForm(page, id(5));
    await expect(page.getByTestId('fee-state')).toContainText("Fee not set for this student's standard and category.");
    await expect(page.getByTestId('save')).toHaveCount(0);

    const student = (await callApi(page, 'GET', `/api/students/${id(1)}`)).body;
    const left = await callApi(page, 'PUT', `/api/students/${id(1)}/left`, { version: student.version });
    expect(left.status).toBe(200);
    await openForm(page, id(1));
    await page.getByLabel('Receipt no. (from the book)').fill('3140');
    await page.getByLabel('Year fee').fill('100');
    await saveAndConfirm(page);
    await expect(page).toHaveURL(/\/finance\/receipts\/\d+$/);
  });

  test('FR-007: before the structure is finalised the form says so', async ({ page }) => {
    await clearMasterData(page);
    data = await seed(page, false);

    await openForm(page, id(1));
    await expect(page.getByTestId('fee-state')).toContainText('not finalised');
    await expect(page.getByTestId('save')).toHaveCount(0);
  });

  test('FR-016: two windows - a receipt saved in one makes the other show the balance-changed pop-up, nothing is saved until Refresh (scenario 15)', async ({
    page,
    context,
  }) => {
    await openForm(page, id(1));
    const other = await context.newPage();
    await openForm(other, id(1));

    await page.getByLabel('Receipt no. (from the book)').fill('3150');
    await page.getByLabel('Year fee').fill('1000');
    await saveAndConfirm(page);
    await expect(page).toHaveURL(/\/finance\/receipts\/\d+$/);

    await other.getByLabel('Receipt no. (from the book)').fill('3151');
    await other.getByLabel('Year fee').fill('500');
    await saveAndConfirm(other);
    await expect(other.getByRole('heading', { name: 'The balance has changed' })).toBeVisible();
    expect((await getFeeCollection(page, id(1))).receipts).toHaveLength(1);

    await other.getByRole('button', { name: 'Refresh' }).click();
    await expect(other.getByRole('heading', { name: 'The balance has changed' })).toHaveCount(0);
    await expect(other.getByTestId('strip-paid')).toHaveText('1,000.00');
    await expect(other.getByLabel('Receipt no. (from the book)')).toHaveValue('3151');
    await saveAndConfirm(other);
    await expect(other).toHaveURL(/\/finance\/receipts\/\d+$/);
    expect((await getFeeCollection(page, id(1))).receipts).toHaveLength(2);
  });

  test('FR-014, FR-009: layout - one Finance pill, the student found by GR No., big controls, no cancel link, no fund note, no serial no. before saving', async ({ page }) => {
    await page.goto('/finance');
    await expect(page).toHaveURL(/\/finance\/fee-collection$/);
    const bar = page.getByRole('navigation', { name: 'Main' });
    await expect(bar.getByRole('link', { name: 'Finance' })).toHaveClass(/bg-accent/);
    await expect(page.getByRole('navigation', { name: 'Finance' }).getByRole('link')).toHaveText(['Fee collection']);

    await page.getByLabel('Student (name or GR No.)').fill('E2E-1');
    await page.getByTestId('student-results').getByRole('button').first().click();
    await expect(page).toHaveURL(/studentId=\d+/);
    await expect(page.getByTestId('strip')).toBeVisible();
    await expect(page.getByTestId('strip-net')).toHaveText('36,500.00');
    await expect(page.getByTestId('strip-due')).toContainText('2030');

    for (const label of ['Receipt no. (from the book)', 'Date', 'Registration fee', 'Year fee', 'Payment mode']) {
      const box = await page.getByLabel(label).boundingBox();
      expect(box?.height ?? 0, label).toBeGreaterThanOrEqual(44);
    }
    await expect(page.getByTestId('save')).toBeVisible();
    const body = page.locator('main');
    await expect(body).not.toContainText(/cancel a receipt|Need to cancel/i);
    await expect(body).not.toContainText(/fund ledger/i);
    await expect(body).not.toContainText(/serial no/i);
    await expect(page.getByRole('link', { name: /cancel/i })).toHaveCount(0);
    for (const hidden of ['Fee tracker', 'Payroll', 'Expenses and fund']) {
      await expect(page.getByRole('navigation', { name: 'Finance' })).not.toContainText(hidden);
    }
  });
});
