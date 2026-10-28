import { expect, Locator, Page, test } from '@playwright/test';
import { addFeeRow, createFeeMasterData, FeeMasterData, finaliseYear, getFeeStructure } from './support/fees';
import { callApi, clearMasterData } from './support/master-data';
import { signInAsTestAdmin } from './support/test-data';

// Synthetic values only (constitution VI). Quickstart scenarios 1 to 5, 16, 17 and the layout checks of section 4.

interface RowValues {
  standard: string;
  category?: string;
  amount: string;
  registrationFee?: string;
  dueDate?: string;
}

async function openFees(page: Page): Promise<void> {
  await page.goto('/setup/fees');
  await expect(page.getByRole('heading', { name: 'Fee structure', exact: true })).toBeVisible();
  await expect(page.getByTestId('fee-card-title')).toBeVisible();
}

const rowAt = (page: Page, index: number): Locator => page.getByTestId('fee-row').nth(index);

async function fillRow(row: Locator, values: RowValues): Promise<void> {
  await row.getByLabel('Standard').selectOption({ label: values.standard });
  await row.getByLabel('Category').selectOption({ label: values.category ?? 'All categories' });
  await row.getByLabel('Yearly fee').fill(values.amount);
  await row.getByLabel('Registration fee').fill(values.registrationFee ?? '');
  await row.getByLabel('Due date').fill(values.dueDate ?? '');
}

/** Adds a row on the screen and waits until the saved row is in the table. */
async function addRowOnScreen(page: Page, values: RowValues, expectedRows: number): Promise<void> {
  await page.getByRole('button', { name: 'Add row' }).click();
  const row = rowAt(page, expectedRows - 1);
  await fillRow(row, values);
  await row.getByRole('button', { name: 'Save row' }).click();
  await expect(row.getByLabel('Yearly fee')).toHaveValue(/^\d+\.\d{2}$/); // the table was rebuilt from the saved row
  await expect(page.getByTestId('fee-row')).toHaveCount(expectedRows);
}

test.describe('fee structure screen', () => {
  let master: FeeMasterData;

  test.beforeEach(async ({ page }) => {
    await signInAsTestAdmin(page);
    await clearMasterData(page);
    master = await createFeeMasterData(page, 2);
  });

  test('FR-001, FR-011, FR-012: the admin adds rows and sees the amounts with two decimals', async ({ page }) => {
    await openFees(page);
    await expect(page.getByTestId('fee-status')).toHaveText('Draft');

    await addRowOnScreen(
      page,
      { standard: 'Fee Std 2', amount: '36000', registrationFee: '500', dueDate: '2030-07-15' },
      1,
    );
    await addRowOnScreen(page, { standard: 'Fee Std 2', category: 'O/S', amount: '40,000.5' }, 2);
    await addRowOnScreen(page, { standard: 'Fee Std 1', amount: '30000' }, 3);

    // The server lists the rows by the standard display order, then the category.
    await expect(rowAt(page, 0).getByLabel('Yearly fee')).toHaveValue('30000.00');
    await expect(rowAt(page, 1).getByLabel('Yearly fee')).toHaveValue('36000.00');
    await expect(rowAt(page, 1).getByLabel('Registration fee')).toHaveValue('500.00');
    await expect(rowAt(page, 2).getByLabel('Yearly fee')).toHaveValue('40000.50');

    const structure = await getFeeStructure(page, master.yearId);
    expect(structure.rows.map((row) => row.amount)).toEqual([30000, 36000, 40000.5]);
    expect(structure.rows[2].registrationFee).toBeNull();
    expect(structure.rows[1].dueDate).toBe('2030-07-15');
  });

  test('FR-001, FR-011: a second row for the same standard and category and bad amounts are refused beside the field', async ({
    page,
  }) => {
    await addFeeRow(page, master.yearId, { standardId: master.standards[1].id, amount: 36000 });
    await openFees(page);

    await page.getByRole('button', { name: 'Add row' }).click();
    const row = rowAt(page, 1);
    await fillRow(row, { standard: 'Fee Std 2', amount: '1000' });
    await row.getByRole('button', { name: 'Save row' }).click();
    await expect(row.getByRole('alert').first()).toBeVisible(); // duplicate, beside the field

    await fillRow(row, { standard: 'Fee Std 1', amount: '-1' });
    await row.getByRole('button', { name: 'Save row' }).click();
    await expect(row.getByRole('alert')).toHaveCount(1);

    await fillRow(row, { standard: 'Fee Std 1', amount: '100.123' });
    await row.getByRole('button', { name: 'Save row' }).click();
    await expect(row.getByRole('alert')).toHaveCount(1);

    await fillRow(row, { standard: 'Fee Std 1', amount: '' });
    await row.getByRole('button', { name: 'Save row' }).click();
    await expect(row.getByRole('alert')).toHaveCount(1);

    expect((await getFeeStructure(page, master.yearId)).rows).toHaveLength(1);
  });

  test('FR-002: a Draft row is edited at once and a delete asks to confirm', async ({ page }) => {
    await addFeeRow(page, master.yearId, { standardId: master.standards[1].id, amount: 36000 });
    await addFeeRow(page, master.yearId, { standardId: master.standards[0].id, amount: 30000 });
    await openFees(page);

    await rowAt(page, 0).getByLabel('Yearly fee').fill('37000');
    await rowAt(page, 0).getByRole('button', { name: 'Save row' }).click();
    await expect(page.getByText('The fee row was saved.')).toBeVisible();
    expect((await getFeeStructure(page, master.yearId)).rows[0].amount).toBe(37000);

    await rowAt(page, 1).getByRole('button', { name: 'Delete' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Cancel' }).click();
    expect((await getFeeStructure(page, master.yearId)).rows).toHaveLength(2);

    await rowAt(page, 1).getByRole('button', { name: 'Delete' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Delete' }).click();
    await expect(page.getByTestId('fee-row')).toHaveCount(1);
    expect((await getFeeStructure(page, master.yearId)).rows).toHaveLength(1);
  });

  test('FR-002: finalising a year with no rows is refused', async ({ page }) => {
    await openFees(page);

    await page.getByRole('button', { name: 'Finalise', exact: true }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Finalise' }).click();

    await expect(page.getByTestId('finalise-error')).toHaveText('Add at least one fee row before finalising.');
    await expect(page.getByTestId('fee-status')).toHaveText('Draft');
  });

  test('FR-002, FR-003: finalising asks to confirm, then the rows can no longer be added, edited or deleted', async ({
    page,
  }) => {
    await addFeeRow(page, master.yearId, { standardId: master.standards[1].id, amount: 36000, registrationFee: 500 });
    await openFees(page);

    await page.getByRole('button', { name: 'Finalise', exact: true }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Cancel' }).click();
    await expect(page.getByTestId('fee-status')).toHaveText('Draft');

    await page.getByRole('button', { name: 'Finalise', exact: true }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Finalise' }).click();

    await expect(page.getByTestId('fee-status')).toHaveText('Finalised');
    await expect(page.getByRole('button', { name: 'Add row' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Save row' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Delete' })).toHaveCount(0);
    await expect(rowAt(page, 0)).toContainText('36,000.00');
    await expect(rowAt(page, 0)).toContainText('500.00');
    expect((await getFeeStructure(page, master.yearId)).status).toBe('Finalised');
  });

  test('FR-013: two windows - the second save of the same row is refused and the latest values are shown', async ({
    page,
    context,
  }) => {
    await addFeeRow(page, master.yearId, { standardId: master.standards[1].id, amount: 36000 });
    await openFees(page);
    const other = await context.newPage();
    await openFees(other);

    await rowAt(page, 0).getByLabel('Yearly fee').fill('37000');
    await rowAt(page, 0).getByRole('button', { name: 'Save row' }).click();
    await expect(page.getByText('The fee row was saved.')).toBeVisible();

    await rowAt(other, 0).getByLabel('Yearly fee').fill('38000');
    await rowAt(other, 0).getByRole('button', { name: 'Save row' }).click();

    await expect(rowAt(other, 0).getByLabel('Yearly fee')).toHaveValue('37000.00'); // reloaded with the latest values
    expect((await getFeeStructure(page, master.yearId)).rows[0].amount).toBe(37000);
  });

  test('FR-013: two windows - after finalising in one, the stale edit in the other reloads as Finalised without a revise warning', async ({
    page,
    context,
  }) => {
    await addFeeRow(page, master.yearId, { standardId: master.standards[1].id, amount: 36000 });
    await openFees(page);
    const other = await context.newPage();
    await openFees(other);

    await finaliseYear(page, master.yearId);

    await rowAt(other, 0).getByLabel('Yearly fee').fill('39000');
    await rowAt(other, 0).getByRole('button', { name: 'Save row' }).click();

    await expect(other.getByTestId('fee-status')).toHaveText('Finalised');
    await expect(other.getByRole('dialog')).toHaveCount(0);
    expect((await getFeeStructure(page, master.yearId)).rows[0].amount).toBe(36000);
  });

  test('FR-013: while no academic year covers today the fee screen is closed as in F0', async ({ page }) => {
    await clearMasterData(page, { currentYear: false });

    await page.goto('/setup/fees');

    await expect(page).toHaveURL(/\/setup\/academic-years$/);
    await expect(page.getByTestId('no-current-year')).toBeVisible();
  });

  test('FR-001, FR-011: layout - a card with the year in the title, a status tag, 44 px controls and a table that scrolls sideways on a phone', async ({
    page,
  }) => {
    await addFeeRow(page, master.yearId, { standardId: master.standards[1].id, amount: 36000 });
    await openFees(page);

    await expect(page.getByTestId('fee-card-title')).toContainText('Fee structure for');
    await expect(page.getByTestId('fee-status')).toBeVisible();
    for (const control of [
      rowAt(page, 0).getByLabel('Yearly fee'),
      rowAt(page, 0).getByLabel('Standard'),
      rowAt(page, 0).getByRole('button', { name: 'Save row' }),
      page.getByRole('button', { name: 'Add row' }),
      page.getByRole('button', { name: 'Finalise', exact: true }),
    ]) {
      expect((await control.boundingBox())?.height ?? 0).toBeGreaterThanOrEqual(44);
    }

    await page.setViewportSize({ width: 375, height: 800 });
    const scroller = page.getByTestId('fee-table-scroll');
    expect(await scroller.evaluate((el) => el.scrollWidth > el.clientWidth)).toBe(true);
    const offenders = await page.evaluate(() =>
      [...document.querySelectorAll('body *')]
        .filter((el) => el.getBoundingClientRect().right > window.innerWidth + 1 && !el.closest('[data-testid=fee-table-scroll], nav'))
        .map((el) => el.tagName + '.' + el.className)
        .slice(0, 8),
    );
    expect(offenders).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });

  test('FR-012: saved rows and the finalise are in the audit log under the Fees area', async ({ page }) => {
    await openFees(page);
    await addRowOnScreen(page, { standard: 'Fee Std 1', amount: '30000' }, 1);
    await finaliseYear(page, master.yearId);

    const log = (await callApi(page, 'POST', '/api/audit-log/search', { first: 0, rows: 50, filters: {} })).body.rows;
    const actions = log.filter((entry: { area: string }) => entry.area === 'Fees').map((entry: { action: string }) => entry.action);
    expect(actions).toContain('Create');
    expect(actions).toContain('Finalise');
  });
});
