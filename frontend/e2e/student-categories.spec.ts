import { expect, Page, test } from '@playwright/test';
import { callApi, clearMasterData } from './support/master-data';
import { signInAsTestAdmin } from './support/test-data';

async function openCategories(page: Page): Promise<void> {
  await page.goto('/master-data/student-categories');
  await expect(page.getByRole('heading', { name: 'Student categories' })).toBeVisible();
}

test.describe('student categories', () => {
  test.beforeEach(async ({ page }) => {
    await signInAsTestAdmin(page);
    await clearMasterData(page);
  });

  test('FR-018: the seven seeded categories are listed', async ({ page }) => {
    await openCategories(page);

    for (const code of ['O/S', 'CEW', 'W', 'STAFF', 'S STAFF', 'RTE', 'CASUAL']) {
      await expect(page.getByRole('cell', { name: code, exact: true }).first()).toBeVisible();
    }
  });

  test('FR-019: the admin adds a category after choosing Yes or No for "Affects fees"', async ({ page }) => {
    await openCategories(page);
    await page.getByRole('button', { name: 'Add category' }).click();
    await page.getByLabel('Code', { exact: true }).fill('NEW1');
    await page.getByLabel('Name', { exact: true }).fill('New one');
    await page.getByLabel('Yes', { exact: true }).check();
    await page.getByRole('button', { name: 'Save' }).click();

    await expect(page.getByRole('row', { name: /NEW1/ })).toBeVisible();
  });

  test('FR-019: a duplicate code is refused beside the field', async ({ page }) => {
    await openCategories(page);
    await page.getByRole('button', { name: 'Add category' }).click();
    await page.getByLabel('Code', { exact: true }).fill('RTE');
    await page.getByLabel('Name', { exact: true }).fill('Another');
    await page.getByLabel('No', { exact: true }).check();
    await page.getByRole('button', { name: 'Save' }).click();

    await expect(page.getByRole('alert').first()).toBeVisible();
  });

  test('FR-019: "Affects fees" has no pre-selected value and saving without a choice is refused', async ({ page }) => {
    await openCategories(page);
    await page.getByRole('button', { name: 'Add category' }).click();

    await expect(page.getByLabel('Yes', { exact: true })).not.toBeChecked();
    await expect(page.getByLabel('No', { exact: true })).not.toBeChecked();

    await page.getByLabel('Code', { exact: true }).fill('NEW2');
    await page.getByLabel('Name', { exact: true }).fill('No choice');
    await page.getByRole('button', { name: 'Save' }).click();
    await expect(page.getByText('Choose whether this category affects fees.')).toBeVisible();
  });

  test('FR-046: a category changed in another window shows the latest values on save', async ({ page }) => {
    const created = (
      await callApi(page, 'POST', '/api/student-categories', { code: 'NEW3', name: 'Third', affectsFees: false })
    ).body;
    await openCategories(page);
    await page.getByRole('row', { name: /NEW3/ }).getByRole('button', { name: 'Edit' }).click();

    await callApi(page, 'PUT', `/api/student-categories/${created.id}`, {
      code: 'NEW3',
      name: 'Third other',
      affectsFees: true,
      version: created.version,
    });
    await page.getByLabel('Name', { exact: true }).fill('Third mine');
    await page.getByRole('button', { name: 'Save' }).click();

    await expect(page.getByTestId('latest-values')).toContainText('Third other');
  });
});
