import { expect, Page, test } from '@playwright/test';
import { callApi, clearMasterData } from './support/master-data';
import { academicYearAround, signInAsTestAdmin } from './support/test-data';

async function openDivisions(page: Page): Promise<void> {
  await page.goto('/master-data/divisions');
  await expect(page.getByRole('heading', { name: 'Divisions' })).toBeVisible();
}

test.describe('divisions', () => {
  let yearId: number;
  let standardId: number;

  test.beforeEach(async ({ page }) => {
    await signInAsTestAdmin(page);
    await clearMasterData(page);
    yearId = (await callApi(page, 'POST', '/api/academic-years', academicYearAround())).body.id;
    standardId = (await callApi(page, 'POST', '/api/standards', { name: 'Standard 1', displayOrder: 1 })).body.id;
  });

  test('FR-016: the admin adds a division for a year and a standard', async ({ page }) => {
    await openDivisions(page);
    await page.getByRole('button', { name: 'Add division' }).click();
    await page.getByLabel('Academic year', { exact: true }).selectOption({ index: 1 });
    await page.getByLabel('Standard', { exact: true }).selectOption({ index: 1 });
    await page.getByLabel('Division', { exact: true }).fill('A');
    await page.getByRole('button', { name: 'Save' }).click();

    await expect(page.getByRole('row', { name: /Standard 1/ })).toBeVisible();
  });

  test('FR-016: the form offers only active standards', async ({ page }) => {
    const inactive = (await callApi(page, 'POST', '/api/standards', { name: 'Standard 2', displayOrder: 2 })).body;
    await callApi(page, 'PUT', `/api/standards/${inactive.id}/active`, { active: false, version: inactive.version });
    await openDivisions(page);
    await page.getByRole('button', { name: 'Add division' }).click();

    const options = page.getByLabel('Standard', { exact: true }).locator('option');
    await expect(options).toHaveText(['Choose the standard', 'Standard 1']);
  });

  test('FR-017, FR-021: a division is marked inactive instead of deleted', async ({ page }) => {
    await callApi(page, 'POST', '/api/divisions', { academicYearId: yearId, standardId, name: 'A' });
    await openDivisions(page);

    const row = page.getByRole('row', { name: /Standard 1/ });
    await row.getByRole('button', { name: 'Mark inactive' }).click();
    await expect(row.getByRole('button', { name: 'Mark active' })).toBeVisible();
  });

  test('FR-046: a division changed in another window shows the latest values on save', async ({ page }) => {
    const division = (await callApi(page, 'POST', '/api/divisions', { academicYearId: yearId, standardId, name: 'A' })).body;
    await openDivisions(page);
    await page.getByRole('row', { name: /Standard 1/ }).getByRole('button', { name: 'Edit' }).click();

    await callApi(page, 'PUT', `/api/divisions/${division.id}`, {
      academicYearId: yearId,
      standardId,
      name: 'B',
      version: division.version,
    });
    await page.getByLabel('Division', { exact: true }).fill('C');
    await page.getByRole('button', { name: 'Save' }).click();

    await expect(page.getByTestId('latest-values')).toContainText('B');
  });
});
