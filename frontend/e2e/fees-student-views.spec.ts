import { expect, Page, test } from '@playwright/test';
import { addFeeRow, createFeeMasterData, FeeMasterData, finaliseYear } from './support/fees';
import { clearMasterData } from './support/master-data';
import { createStudent, studentInput } from './support/students';
import { signInAsTestAdmin } from './support/test-data';
import { readFirstSheetTyped } from './support/xlsx';

// Synthetic values only (constitution VI). Quickstart scenarios 6, 11, 15, 21 and 23.

interface Seed {
  master: FeeMasterData;
  /** Student ids by their number n (GR No. E2E-n). */
  ids: Map<number, number>;
}

const category = (master: FeeMasterData, code: string) => {
  const found = master.categories.find((item) => item.code === code);
  if (!found) {
    throw new Error(`the seeded category ${code} is missing`);
  }
  return found.id;
};

async function register(page: Page, master: FeeMasterData, n: number, standardIndex: number, code: string, ids: Map<number, number>) {
  const standard = master.standards[standardIndex];
  const student = await createStudent(page, {
    ...studentInput(n, {
      yearId: master.yearId,
      standardId: standard.id,
      divisionId: standard.divisionId,
      categoryId: category(master, code),
    }),
  });
  ids.set(n, student.id);
}

/**
 * Standard 1 has one all-categories row, standard 2 has an all-categories row with a registration fee and an O/S row
 * without one, standard 3 has no row. Students: 1 STAFF in 2, 2 O/S in 2, 3 CEW in 1, 4 STAFF in 3.
 */
async function seed(page: Page): Promise<Seed> {
  const master = await createFeeMasterData(page, 3);
  await addFeeRow(page, master.yearId, { standardId: master.standards[0].id, amount: 28000 });
  await addFeeRow(page, master.yearId, {
    standardId: master.standards[1].id,
    amount: 36000,
    registrationFee: 500,
    dueDate: '2030-07-15',
  });
  await addFeeRow(page, master.yearId, {
    standardId: master.standards[1].id,
    categoryId: category(master, 'O/S'),
    amount: 40000.5,
  });
  const ids = new Map<number, number>();
  await register(page, master, 1, 1, 'STAFF', ids);
  await register(page, master, 2, 1, 'O/S', ids);
  await register(page, master, 3, 0, 'CEW', ids);
  await register(page, master, 4, 2, 'STAFF', ids);
  return { master, ids };
}

const text = (page: Page, id: string) => page.getByTestId(id).innerText();

async function openList(page: Page): Promise<void> {
  await page.goto('/students');
  await expect(page.getByRole('heading', { name: 'Students', exact: true })).toBeVisible();
  await expect(page.locator('tbody tr')).toHaveCount(4);
}

const listRow = (page: Page, grNo: string) => page.locator('tbody tr', { hasText: grNo });

test.describe('fees on the student views', () => {
  let data: Seed;

  test.beforeEach(async ({ page }) => {
    await signInAsTestAdmin(page);
    await clearMasterData(page);
    data = await seed(page);
  });

  test('FR-009, FR-011: before the structure is finalised the profile and the list say so (scenario 15)', async ({ page }) => {
    await page.goto(`/students/${data.ids.get(1)}`);
    await expect(page.getByTestId('fee-state')).toHaveText('Fee structure not finalised');
    await expect(page.getByTestId('fee-total-due')).toHaveText('0.00');

    await openList(page);
    await expect(listRow(page, 'E2E-1').getByTestId('total-fee')).toHaveText('Not finalised');
  });

  test('FR-005, FR-009, FR-011: after finalising each profile shows its fee with two decimals (scenario 6)', async ({ page }) => {
    await finaliseYear(page, data.master.yearId);

    await page.goto(`/students/${data.ids.get(1)}`);
    await expect(page.getByTestId('fee-year-fee')).toHaveText('36,000.00');
    expect(await text(page, 'fee-registration')).toBe('500.00');
    expect(await text(page, 'fee-total')).toBe('36,500.00');
    expect(await text(page, 'fee-due-date')).toBe('15-07-2030');
    expect(await text(page, 'fee-total-due')).toBe('36,500.00');

    await page.goto(`/students/${data.ids.get(2)}`);
    await expect(page.getByTestId('fee-year-fee')).toHaveText('40,000.50'); // the O/S row wins
    expect(await text(page, 'fee-registration')).toBe('500.00'); // from the all-categories row
    expect(await text(page, 'fee-total')).toBe('40,500.50');

    await page.goto(`/students/${data.ids.get(3)}`);
    await expect(page.getByTestId('fee-year-fee')).toHaveText('28,000.00');
    await expect(page.getByTestId('fee-registration')).toHaveCount(0);
    expect(await text(page, 'fee-total')).toBe('28,000.00');

    await page.goto(`/students/${data.ids.get(4)}`);
    await expect(page.getByTestId('fee-state')).toHaveText('Fee not set');
    expect(await text(page, 'fee-total-due')).toBe('0.00');

    const block = page.getByTestId('fees-block');
    for (const absent of ['Receipt', 'Paid', 'Pending', 'Collect']) {
      await expect(block).not.toContainText(absent);
    }
  });

  test('FR-010, FR-011: the list has a Total fee column with the amount, Fee not set or Not finalised, and no paid or pending column (scenarios 6, 23)', async ({
    page,
  }) => {
    await finaliseYear(page, data.master.yearId);
    await openList(page);

    await expect(page.getByRole('button', { name: /^Total fee/ })).toBeVisible();
    await expect(listRow(page, 'E2E-1').getByTestId('total-fee')).toHaveText('36,500.00');
    await expect(listRow(page, 'E2E-2').getByTestId('total-fee')).toHaveText('40,500.50');
    await expect(listRow(page, 'E2E-3').getByTestId('total-fee')).toHaveText('28,000.00');
    await expect(listRow(page, 'E2E-4').getByTestId('total-fee')).toHaveText('Fee not set');
    for (const absent of ['Paid', 'Pending', 'Fee status']) {
      await expect(page.locator('thead')).not.toContainText(absent);
    }
  });

  test('FR-006, FR-009: a student registered after finalising has a fee on the profile at once (scenario 11)', async ({ page }) => {
    await finaliseYear(page, data.master.yearId);
    await register(page, data.master, 5, 1, 'STAFF', data.ids);

    await page.goto(`/students/${data.ids.get(5)}`);

    await expect(page.getByTestId('fee-year-fee')).toHaveText('36,000.00');
    expect(await text(page, 'fee-total')).toBe('36,500.00');
  });

  test('FR-010, FR-011: the Excel export has Total fee as a numeric cell and blank where there is no fee (scenarios 21, 23)', async ({
    page,
  }) => {
    await finaliseYear(page, data.master.yearId);
    await openList(page);

    const [download] = await Promise.all([page.waitForEvent('download'), page.getByTestId('export').click()]);

    const sheet = readFirstSheetTyped(await download.path());
    const headers = sheet[0].map((cell) => cell.value);
    const fee = headers.indexOf('Total fee');
    const state = headers.indexOf('Fee state');
    const grNo = headers.indexOf('GR No.');
    expect(fee).toBeGreaterThanOrEqual(0);
    expect(state).toBeGreaterThanOrEqual(0);
    const row = (n: string) => sheet.slice(1).find((cells) => cells[grNo].value === n) as (typeof sheet)[number];

    // A number cell: the file holds the amount, the cell format shows two decimals.
    for (const [n, amount] of [['E2E-1', 36500], ['E2E-2', 40500.5], ['E2E-3', 28000]] as const) {
      expect(row(n)[fee].kind).toBe('number');
      expect(Number(row(n)[fee].value)).toBe(amount);
    }
    expect(row('E2E-4')[fee].kind).toBe('empty');
    expect(row('E2E-4')[state].value).toBe('Fee not set');
    expect(headers.join('|')).not.toMatch(/paid|pending/i);
  });
});
