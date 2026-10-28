import { expect, Page, test } from '@playwright/test';
import { addFeeRow, createFeeMasterData, FeeMasterData, finaliseYear, getFeeStructure } from './support/fees';
import { callApi, clearMasterData } from './support/master-data';
import { createStudent, studentInput } from './support/students';
import { signInAsTestAdmin } from './support/test-data';

// Synthetic values only (constitution VI). Quickstart scenarios 7 to 10 and 16 on a finalised year.

interface Seed {
  master: FeeMasterData;
  /** Student ids by number n (GR No. E2E-n). */
  ids: Map<number, number>;
}

const categoryId = (master: FeeMasterData, code: string) =>
  master.categories.find((category) => category.code === code)?.id as number;

/**
 * Standard 1 has an all-categories row of 30,000.00 (the "Std 9" of the quickstart), standard 2 has 36,000.00 for all
 * categories and 40,000.00 for O/S. Students: 1 in standard 1, 2 STAFF and 3 O/S in standard 2. Finalised.
 */
async function seedFinalised(page: Page): Promise<Seed> {
  const master = await createFeeMasterData(page, 2);
  await addFeeRow(page, master.yearId, { standardId: master.standards[0].id, amount: 30000 });
  await addFeeRow(page, master.yearId, { standardId: master.standards[1].id, amount: 36000, registrationFee: 500 });
  await addFeeRow(page, master.yearId, {
    standardId: master.standards[1].id,
    categoryId: categoryId(master, 'O/S'),
    amount: 40000,
  });
  const ids = new Map<number, number>();
  const people: [number, number, string][] = [
    [1, 0, 'STAFF'],
    [2, 1, 'STAFF'],
    [3, 1, 'O/S'],
  ];
  for (const [n, standardIndex, code] of people) {
    const standard = master.standards[standardIndex];
    const student = await createStudent(
      page,
      studentInput(n, {
        yearId: master.yearId,
        standardId: standard.id,
        divisionId: standard.divisionId,
        categoryId: categoryId(master, code),
      }),
    );
    ids.set(n, student.id);
  }
  await finaliseYear(page, master.yearId);
  return { master, ids };
}

async function openFees(page: Page): Promise<void> {
  await page.goto('/setup/fees');
  await expect(page.getByTestId('fee-card-title')).toBeVisible();
}

const rowAt = (page: Page, index: number) => page.getByTestId('fee-row').nth(index);
const warning = (page: Page) => page.getByTestId('revise-warning');

async function feeOnProfile(page: Page, id: number): Promise<string> {
  await page.goto(`/students/${id}`);
  const state = page.getByTestId('fee-state');
  const year = page.getByTestId('fee-year-fee');
  await expect(state.or(year)).toBeVisible();
  return (await state.count()) > 0 ? await state.innerText() : await year.innerText();
}

test.describe('revise a finalised fee structure', () => {
  let data: Seed;

  test.beforeEach(async ({ page }) => {
    await signInAsTestAdmin(page);
    await clearMasterData(page);
    data = await seedFinalised(page);
  });

  test('FR-003: a finalised year shows Revise instead of Add, Edit and Delete', async ({ page }) => {
    await openFees(page);

    await expect(page.getByTestId('fee-status')).toHaveText('Finalised');
    await expect(page.getByRole('button', { name: 'Revise' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Add row' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Save row' })).toHaveCount(0);

    await page.getByRole('button', { name: 'Revise' }).click();
    await expect(page.getByTestId('revise-notice')).toContainText('This year is being revised.');
    await expect(page.getByRole('button', { name: 'Save row' })).toHaveCount(3);
    await page.getByRole('button', { name: 'Done revising' }).click();
    await expect(page.getByTestId('revise-notice')).toHaveCount(0);
  });

  test('FR-004, FR-005: deleting a row through Revise warns with the student count, then those students show Fee not set (scenario 7)', async ({
    page,
  }) => {
    await openFees(page);
    await page.getByRole('button', { name: 'Revise' }).click();

    await rowAt(page, 0).getByRole('button', { name: 'Delete' }).click();

    await expect(warning(page)).toContainText("Every student's fee for the year will be recalculated.");
    await expect(page.getByTestId('revise-count')).toContainText('3 students');
    expect((await getFeeStructure(page, data.master.yearId)).rows).toHaveLength(3); // nothing changed yet

    await page.getByRole('button', { name: 'Confirm and recalculate' }).click();
    await expect(page.getByTestId('fee-row')).toHaveCount(2);

    expect(await feeOnProfile(page, data.ids.get(1) as number)).toBe('Fee not set');
    expect(await feeOnProfile(page, data.ids.get(2) as number)).toBe('36,000.00');
  });

  test('FR-004: Cancel at the warning changes nothing (scenario 8)', async ({ page }) => {
    await openFees(page);
    await page.getByRole('button', { name: 'Revise' }).click();

    await rowAt(page, 1).getByLabel('Yearly fee').fill('37000');
    await rowAt(page, 1).getByRole('button', { name: 'Save row' }).click();
    await expect(warning(page)).toBeVisible();
    await page.getByRole('dialog').getByRole('button', { name: 'Cancel' }).click();

    await expect(warning(page)).toHaveCount(0);
    const structure = await getFeeStructure(page, data.master.yearId);
    expect(structure.rows.map((row) => row.amount)).toEqual([30000, 36000, 40000]);
    expect(await feeOnProfile(page, data.ids.get(2) as number)).toBe('36,000.00');
  });

  test('FR-004, FR-005, FR-012: Confirm recalculates the students of that row only (scenario 9)', async ({ page }) => {
    await openFees(page);
    await page.getByRole('button', { name: 'Revise' }).click();

    await rowAt(page, 1).getByLabel('Yearly fee').fill('37000');
    await rowAt(page, 1).getByRole('button', { name: 'Save row' }).click();
    await page.getByRole('button', { name: 'Confirm and recalculate' }).click();
    await expect(page.getByText(/Every student's fee was recalculated/)).toBeVisible();

    expect(await feeOnProfile(page, data.ids.get(2) as number)).toBe('37,000.00');
    expect(await feeOnProfile(page, data.ids.get(3) as number)).toBe('40,000.00'); // the O/S row is unchanged

    const log = (await callApi(page, 'POST', '/api/audit-log/search', { first: 0, rows: 50, filters: {} })).body.rows;
    const revises = log.filter((entry: unknown) => JSON.stringify(entry).includes('Revise'));
    expect(revises.length).toBeGreaterThanOrEqual(1);
  });

  test('FR-004: the row API without confirm answers REVISE_CONFIRMATION_REQUIRED with the student count and changes nothing (scenario 10)', async ({
    page,
  }) => {
    const structure = await getFeeStructure(page, data.master.yearId);
    const row = structure.rows[0];

    const result = await callApi(page, 'PUT', `/api/fee-structures/${data.master.yearId}/rows/${row.id}`, {
      version: row.version,
      structureVersion: structure.version,
      standardId: row.standardId,
      categoryId: row.categoryId,
      amount: 31000,
    });

    expect(result.status).toBe(409);
    expect(result.body.code).toBe('REVISE_CONFIRMATION_REQUIRED');
    expect(result.body.current.studentCount).toBe(3);
    expect((await getFeeStructure(page, data.master.yearId)).rows[0].amount).toBe(30000);
  });

  test('FR-013: two windows - a stale edit in the second reloads the latest structure before any warning (scenario 16)', async ({
    page,
    context,
  }) => {
    await openFees(page);
    await page.getByRole('button', { name: 'Revise' }).click();
    const other = await context.newPage();
    await openFees(other);
    await other.getByRole('button', { name: 'Revise' }).click();

    await rowAt(page, 1).getByLabel('Yearly fee').fill('37000');
    await rowAt(page, 1).getByRole('button', { name: 'Save row' }).click();
    await page.getByRole('button', { name: 'Confirm and recalculate' }).click();
    await expect(page.getByText(/Every student's fee was recalculated/)).toBeVisible();

    await rowAt(other, 1).getByLabel('Yearly fee').fill('38000');
    await rowAt(other, 1).getByRole('button', { name: 'Save row' }).click();

    await expect(rowAt(other, 1).getByLabel('Yearly fee')).toHaveValue('37000.00'); // the latest values
    await expect(warning(other)).toHaveCount(0);
    expect((await getFeeStructure(page, data.master.yearId)).rows[1].amount).toBe(37000);
  });
});
