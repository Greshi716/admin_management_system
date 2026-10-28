import { expect, Page, test } from '@playwright/test';
import { addFeeRow, createFeeMasterData, FeeMasterData, finaliseYear } from './support/fees';
import { callApi, clearMasterData } from './support/master-data';
import { createStudent, studentInput } from './support/students';
import { signInAsTestAdmin } from './support/test-data';

// Synthetic values only (constitution VI). Quickstart scenario 12.

interface Seed {
  master: FeeMasterData;
  withFee: number;
  withoutFee: number;
}

/** Standard 1 has a 28,000.00 row; standard 2 has none. One student in each, the year finalised. */
async function seed(page: Page): Promise<Seed> {
  const master = await createFeeMasterData(page, 2);
  await addFeeRow(page, master.yearId, { standardId: master.standards[0].id, amount: 28000 });
  const categoryId = master.categories[0].id;
  const make = async (n: number, index: number) =>
    (
      await createStudent(
        page,
        studentInput(n, {
          yearId: master.yearId,
          standardId: master.standards[index].id,
          divisionId: master.standards[index].divisionId,
          categoryId,
        }),
      )
    ).id;
  const withFee = await make(1, 0);
  const withoutFee = await make(2, 1);
  await finaliseYear(page, master.yearId);
  return { master, withFee, withoutFee };
}

async function openEdit(page: Page, id: number): Promise<void> {
  await page.goto(`/students/${id}/edit`);
  await expect(page.getByRole('heading', { name: 'Edit student' })).toBeVisible();
  await expect(page.getByTestId('brought-forward-section')).toBeVisible();
}

const section = (page: Page) => page.getByTestId('brought-forward-section');
const amount = (page: Page) => section(page).getByLabel('Amount brought forward');
const note = (page: Page) => section(page).getByLabel('Note');
const saveBf = (page: Page) => section(page).getByRole('button', { name: 'Save brought forward' });

test.describe('brought forward', () => {
  let data: Seed;

  test.beforeEach(async ({ page }) => {
    await signInAsTestAdmin(page);
    await clearMasterData(page);
    data = await seed(page);
  });

  test('FR-007, FR-009, FR-011, FR-012: enter 2,500.00, change it to 1,000.50, then clear it (scenario 12)', async ({ page }) => {
    await openEdit(page, data.withFee);
    await expect(section(page)).toContainText('A short reason, at most 200 characters.');

    await amount(page).fill('2,500');
    await note(page).fill('Fees of last year');
    await saveBf(page).click();
    await expect(page.getByText('The brought-forward amount was saved.')).toBeVisible();
    await expect(amount(page)).toHaveValue('2500.00');

    await page.goto(`/students/${data.withFee}`);
    await expect(page.getByTestId('fee-brought-forward')).toContainText('2,500.00');
    await expect(page.getByTestId('fee-brought-forward-note')).toHaveText('Fees of last year');
    await expect(page.getByTestId('fee-total-due')).toHaveText('30,500.00');

    await openEdit(page, data.withFee);
    await amount(page).fill('1000.50');
    await saveBf(page).click();
    await expect(page.getByText('The brought-forward amount was saved.')).toBeVisible();
    await page.goto(`/students/${data.withFee}`);
    await expect(page.getByTestId('fee-total-due')).toHaveText('29,000.50');

    await openEdit(page, data.withFee);
    await section(page).getByRole('button', { name: 'Clear' }).click();
    await expect(page.getByText('The brought-forward amount was saved.')).toBeVisible();
    await page.goto(`/students/${data.withFee}`);
    await expect(page.getByTestId('fee-brought-forward')).toContainText('0.00');
    await expect(page.getByTestId('fee-brought-forward-note')).toHaveCount(0);
    await expect(page.getByTestId('fee-total-due')).toHaveText('28,000.00');

    const log = (await callApi(page, 'POST', '/api/audit-log/search', { first: 0, rows: 50, filters: {} })).body.rows;
    const entries = log.filter((entry: unknown) => JSON.stringify(entry).includes(`BroughtForward#${data.withFee}`));
    expect(entries).toHaveLength(3);
    expect(JSON.stringify(entries)).not.toContain('Fees of last year');
  });

  test('FR-007, FR-009: a student whose fee is not set still shows the brought-forward amount', async ({ page }) => {
    await openEdit(page, data.withoutFee);
    await amount(page).fill('700.25');
    await saveBf(page).click();
    await expect(page.getByText('The brought-forward amount was saved.')).toBeVisible();

    await page.goto(`/students/${data.withoutFee}`);

    await expect(page.getByTestId('fee-state')).toHaveText('Fee not set');
    await expect(page.getByTestId('fee-brought-forward')).toContainText('700.25');
    await expect(page.getByTestId('fee-total-due')).toHaveText('700.25');
  });

  test('FR-011: a bad amount or a long note is refused beside the field and nothing is saved', async ({ page }) => {
    await openEdit(page, data.withFee);

    await amount(page).fill('100.123');
    await saveBf(page).click();
    await expect(section(page).getByRole('alert')).toContainText('two decimals');

    await amount(page).fill('100');
    await note(page).fill('x'.repeat(201));
    await saveBf(page).click();
    await expect(section(page).getByRole('alert')).toContainText('200 characters');

    await page.goto(`/students/${data.withFee}`);
    await expect(page.getByTestId('fee-total-due')).toHaveText('28,000.00');
  });

  test('FR-007: registering a student has no Brought forward section', async ({ page }) => {
    await page.goto('/students/new');
    await expect(page.getByRole('heading', { name: 'Register student' })).toBeVisible();
    await expect(page.getByTestId('brought-forward-section')).toHaveCount(0);
  });
});
