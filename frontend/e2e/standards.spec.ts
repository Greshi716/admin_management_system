import { expect, Page, test } from '@playwright/test';
import { callApi, clearMasterData } from './support/master-data';
import { academicYearAround, signInAsTestAdmin } from './support/test-data';

async function openStandards(page: Page): Promise<void> {
  await page.goto('/master-data/standards');
  await expect(page.getByRole('heading', { name: 'Standards' })).toBeVisible();
}

test.describe('standards', () => {
  test.beforeEach(async ({ page }) => {
    await signInAsTestAdmin(page);
    await clearMasterData(page);
  });

  test('FR-014: the admin adds a standard with a display order and a stream', async ({ page }) => {
    await openStandards(page);
    await page.getByRole('button', { name: 'Add standard' }).click();
    await page.getByLabel('Name', { exact: true }).fill('Standard 11');
    await page.getByLabel('Display order', { exact: true }).fill('11');
    await page.getByLabel('Stream', { exact: true }).selectOption('Science');
    await page.getByRole('button', { name: 'Save' }).click();

    await expect(page.getByRole('row', { name: /Standard 11/ })).toContainText('Science');
  });

  test('FR-015: a duplicate standard name is refused beside the field', async ({ page }) => {
    await callApi(page, 'POST', '/api/standards', { name: 'Standard 1', displayOrder: 1 });
    await openStandards(page);
    await page.getByRole('button', { name: 'Add standard' }).click();
    await page.getByLabel('Name', { exact: true }).fill('Standard 1');
    await page.getByLabel('Display order', { exact: true }).fill('2');
    await page.getByRole('button', { name: 'Save' }).click();

    await expect(page.getByRole('alert').first()).toBeVisible();
  });

  test('FR-020, FR-021: a standard used by a division cannot be deleted, so the admin marks it inactive', async ({
    page,
  }) => {
    const standard = (await callApi(page, 'POST', '/api/standards', { name: 'Standard 1', displayOrder: 1 })).body;
    const year = (await callApi(page, 'POST', '/api/academic-years', academicYearAround())).body;
    await callApi(page, 'POST', '/api/divisions', { academicYearId: year.id, standardId: standard.id, name: 'A' });
    await openStandards(page);

    const row = page.getByRole('row', { name: /Standard 1/ });
    await row.getByRole('button', { name: 'Delete' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Delete' }).click();
    await expect(page.getByText(/in use/i).first()).toBeVisible();

    await row.getByRole('button', { name: 'Mark inactive' }).click();
    await expect(row.getByRole('button', { name: 'Mark active' })).toBeVisible();
  });

  test('FR-046: a standard changed in another window shows the latest values on save', async ({ page }) => {
    const standard = (await callApi(page, 'POST', '/api/standards', { name: 'Standard 1', displayOrder: 1 })).body;
    await openStandards(page);
    await page.getByRole('row', { name: /Standard 1/ }).getByRole('button', { name: 'Edit' }).click();

    await callApi(page, 'PUT', `/api/standards/${standard.id}`, {
      name: 'Standard 1 other',
      displayOrder: 1,
      version: standard.version,
    });
    await page.getByLabel('Name', { exact: true }).fill('Standard 1 mine');
    await page.getByRole('button', { name: 'Save' }).click();

    await expect(page.getByTestId('latest-values')).toContainText('Standard 1 other');
  });
});
