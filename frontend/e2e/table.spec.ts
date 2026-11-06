import { expect, Locator, Page, test } from '@playwright/test';
import { callApi, clearMasterData } from './support/master-data';
import { signInAsTestAdmin } from './support/test-data';
import { readFirstSheet } from './support/xlsx';

/** Thirty standards named Std 01 to Std 30; every odd one is active. The standards list stands for every list. */
async function thirtyStandards(page: Page): Promise<void> {
  for (let i = 1; i <= 30; i++) {
    const created = await callApi(page, 'POST', '/api/standards', {
      name: `Std ${String(i).padStart(2, '0')}`,
      displayOrder: i,
    });
    if (i % 2 === 0) {
      await callApi(page, 'PUT', `/api/standards/${created.body.id}/active`, {
        active: false,
        version: created.body.version,
      });
    }
  }
}

async function openStandards(page: Page): Promise<void> {
  await page.goto('/master-data/standards');
  await expect(page.getByRole('heading', { name: 'Standards' })).toBeVisible();
  await expect(page.getByText('of 30 rows')).toBeVisible();
}

function bodyRows(page: Page): Locator {
  return page.locator('tbody tr');
}

async function firstColumn(page: Page): Promise<string[]> {
  return (await bodyRows(page).locator('td:first-child').allInnerTexts()).map((text) => text.trim());
}

async function rowsPerPage(page: Page, size: string): Promise<void> {
  await page.getByLabel('Rows per page').selectOption(size);
}

test.describe('table behaviours', () => {
  test.beforeEach(async ({ page }) => {
    await signInAsTestAdmin(page);
    await clearMasterData(page);
    await thirtyStandards(page);
  });

  test('FR-029: paging shows one page of rows with the total and moves to the next page', async ({ page }) => {
    await openStandards(page);
    await rowsPerPage(page, '10');
    await expect(page.getByText('Showing 1 to 10 of 30 rows')).toBeVisible();
    await expect(bodyRows(page)).toHaveCount(10);

    await page.getByRole('button', { name: 'Next page' }).click();

    await expect(page.getByText('Showing 11 to 20 of 30 rows')).toBeVisible();
    expect(await firstColumn(page)).toEqual(Array.from({ length: 10 }, (_, i) => `Std ${11 + i}`));
  });

  test('FR-030: search narrows the list as the admin types, without pressing Enter', async ({ page }) => {
    await openStandards(page);

    await page.getByRole('searchbox', { name: 'Search this list' }).fill('std 07');

    await expect(bodyRows(page)).toHaveCount(1);
    await expect(bodyRows(page).first()).toContainText('Std 07');
    await expect(page.getByText('of 1 rows')).toBeVisible();
  });

  test('FR-030: a search that matches nothing shows the empty message', async ({ page }) => {
    await openStandards(page);

    await page.getByRole('searchbox', { name: 'Search this list' }).fill('no such standard');

    await expect(page.getByText('No rows found')).toBeVisible();
  });

  test('FR-031: a column filter works together with the search', async ({ page }) => {
    await openStandards(page);
    await page.getByRole('searchbox', { name: 'Search this list' }).fill('Std 1');
    await expect(page.getByText('of 10 rows')).toBeVisible();

    await page.getByRole('button', { name: 'Filter Active' }).click();
    await page.getByRole('group', { name: 'Filter Active' }).getByRole('combobox').selectOption('true');

    await expect(page.getByText('of 5 rows')).toBeVisible();
    expect(await firstColumn(page)).toEqual(['Std 11', 'Std 13', 'Std 15', 'Std 17', 'Std 19']);
  });

  test('FR-032: sorting applies to the whole list, not just the visible page', async ({ page }) => {
    await openStandards(page);
    await rowsPerPage(page, '10');
    const nameHeading = page.getByRole('button', { name: /^Name/ });

    await nameHeading.click();
    await nameHeading.click();

    await expect(bodyRows(page).first()).toContainText('Std 30');
    await page.getByRole('button', { name: 'Last page' }).click();
    await expect(bodyRows(page).last()).toContainText('Std 01');
  });

  test('FR-033: the downloaded Excel file holds the same rows as the screen, in the same order', async ({ page }) => {
    await openStandards(page);
    await page.getByRole('searchbox', { name: 'Search this list' }).fill('Std 1');
    await expect(page.getByText('of 10 rows')).toBeVisible();
    const nameHeading = page.getByRole('button', { name: /^Name/ });
    await nameHeading.click();
    await nameHeading.click();
    await expect(bodyRows(page).first()).toContainText('Std 19');
    const onScreen = await firstColumn(page);

    const [download] = await Promise.all([
      page.waitForEvent('download'),
      page.getByTestId('export').click(),
    ]);

    expect(download.suggestedFilename()).toMatch(/^standards-\d{4}-\d{2}-\d{2}\.xlsx$/);
    const sheet = readFirstSheet(await download.path());
    expect(sheet[0]).toEqual(['Name', 'Display order', 'Stream', 'Uses stream', 'Active']);
    expect(sheet.slice(1).map((row) => row[0])).toEqual(onScreen);
  });

  test('FR-033: exporting a list with no matches downloads the headings only', async ({ page }) => {
    await openStandards(page);
    await page.getByRole('searchbox', { name: 'Search this list' }).fill('no such standard');
    await expect(page.getByText('No rows found')).toBeVisible();

    const [download] = await Promise.all([
      page.waitForEvent('download'),
      page.getByTestId('export').click(),
    ]);

    expect(readFirstSheet(await download.path())).toEqual([['Name', 'Display order', 'Stream', 'Uses stream', 'Active']]);
  });

  test('FR-026, FR-030: a reversed date range on the audit log shows the message and no rows', async ({ page }) => {
    await page.goto('/audit-log');
    await expect(page.getByRole('heading', { name: 'Audit log' })).toBeVisible();

    await page.getByLabel('From', { exact: true }).fill('2026-03-31');
    await page.getByLabel('To', { exact: true }).fill('2026-03-01');

    await expect(page.getByTestId('range-error')).toBeVisible();
    await expect(page.getByText('No rows found')).toBeVisible();
  });
});
