import { expect, Page, test } from '@playwright/test';
import { addFeeRow, createFeeMasterData, FeeMasterData, finaliseYear } from './support/fees';
import { clearMasterData } from './support/master-data';
import { clearReceipts, getFeeCollection } from './support/receipts';
import { createStudent, studentInput } from './support/students';
import { signInAsTestAdmin } from './support/test-data';

// Synthetic values only (constitution VI). End-to-end flow 1 (quickstart scenarios 1, 8, 15, 16 and the SC-001 timing).
// Standard 1: 36,500.00 with a registration fee of 500.00. The SC-001 timing is a regression guard (the flow needs no
// extra steps and stays quick); it cannot prove how long a person takes (quickstart-results.md says so).

interface Seed {
  master: FeeMasterData;
  studentId: number;
}

const now = new Date();
const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;

async function seed(page: Page): Promise<Seed> {
  const master = await createFeeMasterData(page, 1);
  await addFeeRow(page, master.yearId, { standardId: master.standards[0].id, amount: 36500, registrationFee: 500, dueDate: '2030-07-15' });
  const standard = master.standards[0];
  const student = await createStudent(
    page,
    studentInput(1, { yearId: master.yearId, standardId: standard.id, divisionId: standard.divisionId, categoryId: master.categories[0].id }),
  );
  await finaliseYear(page, master.yearId);
  return { master, studentId: student.id };
}

test.describe('receipt flow', () => {
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

  test('FR-001, FR-003, FR-008, FR-012, FR-014, FR-016, FR-017, FR-018: enter, confirm, print twice, stale window, then profile and list show the receipt (SC-001)', async ({
    page,
    context,
  }) => {
    await page.goto(`/finance/fee-collection?studentId=${data.studentId}`);
    await expect(page.getByTestId('strip')).toBeVisible();

    const started = Date.now();
    await page.getByLabel('Receipt no. (from the book)').fill('4100');
    await page.getByLabel('Registration fee').fill('500');
    await page.getByLabel('Year fee').fill('10000');
    await page.getByTestId('save').click();

    await expect(page.getByTestId('confirm-receipt-no')).toHaveText('4100');
    await expect(page.getByTestId('confirm-total')).toHaveText('10,500.00');
    await expect(page.getByTestId('confirm-student')).toContainText('E2E-1');

    await page.getByRole('button', { name: 'Cancel' }).click();
    await expect(page.getByLabel('Receipt no. (from the book)')).toHaveValue('4100');
    await expect(page.getByLabel('Registration fee')).toHaveValue('500');
    await expect(page.getByLabel('Year fee')).toHaveValue('10000');
    expect((await getFeeCollection(page, data.studentId)).receipts).toHaveLength(0);

    await page.getByTestId('save').click();
    await page.getByRole('button', { name: 'Confirm and save' }).click();
    await expect(page).toHaveURL(/\/finance\/receipts\/\d+$/);
    await expect(page.getByTestId('receipt-total')).toHaveText('10,500.00');
    const saved = Date.now() - started;

    await page.getByTestId('print-receipt').click();
    await expect(page).toHaveURL(/\/finance\/receipts\/\d+\/print$/);
    await expect(page.getByTestId('receipt-sheet')).toBeVisible();
    const printView = Date.now() - started;
    // eslint-disable-next-line no-console
    console.log(`SC-001 timing: saved after ${saved} ms, print view after ${printView} ms (development PC)`);
    expect(saved).toBeLessThan(60_000);
    expect(printView).toBeLessThan(60_000);

    await page.emulateMedia({ media: 'print' });
    await expect(page.getByTestId('print-total')).toContainText('10,500.00');
    await expect(page.getByTestId('print-receipt-no')).toContainText('4100');
    const first = await page.getByTestId('receipt-sheet').innerText();
    await page.reload();
    await page.emulateMedia({ media: 'print' });
    await expect(page.getByTestId('receipt-sheet')).toBeVisible();
    expect(await page.getByTestId('receipt-sheet').innerText()).toBe(first);
    await page.emulateMedia({ media: 'screen' });

    // Two windows: a receipt saved in one makes the other show the "balance has changed" pop-up.
    await page.goto(`/finance/fee-collection?studentId=${data.studentId}`);
    const other = await context.newPage();
    await other.goto(`/finance/fee-collection?studentId=${data.studentId}`);
    await expect(other.getByTestId('strip')).toBeVisible();
    await expect(page.getByTestId('strip')).toBeVisible();

    await page.getByLabel('Receipt no. (from the book)').fill('4101');
    await page.getByLabel('Year fee').fill('1000');
    await page.getByTestId('save').click();
    await page.getByRole('button', { name: 'Confirm and save' }).click();
    await expect(page).toHaveURL(/\/finance\/receipts\/\d+$/);

    await other.getByLabel('Receipt no. (from the book)').fill('4102');
    await other.getByLabel('Year fee').fill('500');
    await other.getByTestId('save').click();
    await other.getByRole('button', { name: 'Confirm and save' }).click();
    await expect(other.getByRole('heading', { name: 'The balance has changed' })).toBeVisible();
    expect((await getFeeCollection(page, data.studentId)).receipts).toHaveLength(2);
    await other.getByRole('button', { name: 'Refresh' }).click();
    await expect(other.getByRole('heading', { name: 'The balance has changed' })).toHaveCount(0);
    await expect(other.getByTestId('strip-paid')).toHaveText('11,500.00');
    await other.close();

    // Profile and Students list: 11,500.00 paid, 25,500.00 pending (36,500.00 + 500.00 - 11,500.00).
    await page.goto(`/students/${data.studentId}`);
    await expect(page.getByTestId('fee-paid')).toHaveText('11,500.00');
    await expect(page.getByTestId('fee-pending')).toHaveText('25,500.00');
    await expect(page.getByTestId('fee-paid-up-to')).not.toHaveText('Nothing paid');
    await expect(page.getByTestId('receipt-link')).toHaveCount(2);

    await page.goto('/students');
    const row = page.locator('tbody tr', { hasText: 'E2E-1' });
    await expect(row.getByTestId('paid')).toHaveText('11,500.00');
    await expect(row.getByTestId('pending')).toHaveText('25,500.00');
    await expect(row.getByTestId('paid-up-to')).not.toHaveText('');
    await expect(row.getByTestId('fee-status')).toHaveText('Partly paid');
  });
});
