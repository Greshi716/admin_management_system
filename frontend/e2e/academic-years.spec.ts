import { expect, Page, test } from '@playwright/test';
import { callApi, clearMasterData } from './support/master-data';
import { academicYearAround, signInAsTestAdmin, YearDates } from './support/test-data';

async function openYears(page: Page): Promise<void> {
  await page.goto('/master-data/academic-years');
  await expect(page.getByRole('heading', { name: 'Academic years' })).toBeVisible();
}

/** Fills the create form; opens it first unless it is already open (it opens by itself while no year covers today). */
async function addYear(page: Page, year: YearDates, options: { alreadyOpen?: boolean } = {}): Promise<void> {
  if (!options.alreadyOpen) {
    await page.getByRole('button', { name: 'Add academic year' }).click();
  }
  await page.getByLabel('Name', { exact: true }).fill(year.name);
  await page.getByLabel('Start date', { exact: true }).fill(year.startDate);
  await page.getByLabel('End date', { exact: true }).fill(year.endDate);
  await page.getByRole('button', { name: 'Save' }).click();
}

test.describe('academic years', () => {
  test.beforeEach(async ({ page }) => {
    await signInAsTestAdmin(page);
    await clearMasterData(page, { currentYear: false });
  });

  test('FR-010: the admin adds an academic year and sees it in the list', async ({ page }) => {
    await openYears(page);
    const year = academicYearAround();
    // no year covers today yet, so the create form is already open (FR-013)
    await expect(page.getByRole('dialog', { name: 'Add academic year' })).toBeVisible();
    await addYear(page, year, { alreadyOpen: true });

    await expect(page.getByRole('row', { name: new RegExp(year.name) })).toBeVisible();
  });

  test('FR-011: a year that overlaps an existing one is refused and names the other year', async ({ page }) => {
    const year = academicYearAround();
    await callApi(page, 'POST', '/api/academic-years', year);
    await openYears(page);

    await addYear(page, { name: 'Overlapping', startDate: year.endDate, endDate: '2099-03-31' });

    await expect(page.getByRole('alert').first()).toContainText(year.name);
  });

  test('FR-012: the year that contains today carries the Current tag and the others do not', async ({ page }) => {
    const thisYear = academicYearAround();
    const nextYear = academicYearAround(new Date(), 1);
    await callApi(page, 'POST', '/api/academic-years', thisYear);
    await callApi(page, 'POST', '/api/academic-years', nextYear);
    await openYears(page);

    const thisRow = page.getByRole('row', { name: new RegExp(thisYear.name) });
    const nextRow = page.getByRole('row', { name: new RegExp(nextYear.name) });
    await expect(thisRow.getByText('Current', { exact: true })).toBeVisible();
    await expect(nextRow).toBeVisible();
    await expect(nextRow.getByText('Current', { exact: true })).toHaveCount(0);
    await expect(page.getByRole('button', { name: /make current/i })).toHaveCount(0);
  });

  test('FR-013: the admin edits the name of a year', async ({ page }) => {
    const year = academicYearAround();
    await callApi(page, 'POST', '/api/academic-years', year);
    await openYears(page);

    await page.getByRole('row', { name: new RegExp(year.name) }).getByRole('button', { name: 'Edit' }).click();
    await page.getByLabel('Name', { exact: true }).fill(`${year.name} edited`);
    await page.getByRole('button', { name: 'Save' }).click();

    await expect(page.getByRole('row', { name: new RegExp(`${year.name} edited`) })).toBeVisible();
  });

  test('FR-020: a year used by a division cannot be deleted and says why', async ({ page }) => {
    const year = academicYearAround();
    const created = (await callApi(page, 'POST', '/api/academic-years', year)).body;
    const standard = (await callApi(page, 'POST', '/api/standards', { name: 'Standard 1', displayOrder: 1 })).body;
    await callApi(page, 'POST', '/api/divisions', { academicYearId: created.id, standardId: standard.id, name: 'A' });
    await openYears(page);

    await page.getByRole('row', { name: new RegExp(year.name) }).getByRole('button', { name: 'Delete' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Delete' }).click();

    await expect(page.getByText(/in use/i).first()).toBeVisible();
    await expect(page.getByRole('row', { name: new RegExp(year.name) })).toBeVisible();
  });

  test('FR-046: saving a year that was changed in another window shows the latest values', async ({ page }) => {
    const year = academicYearAround();
    const created = (await callApi(page, 'POST', '/api/academic-years', year)).body;
    await openYears(page);
    await page.getByRole('row', { name: new RegExp(year.name) }).getByRole('button', { name: 'Edit' }).click();

    // The other window saves first.
    await callApi(page, 'PUT', `/api/academic-years/${created.id}`, {
      ...year,
      name: `${year.name} other`,
      version: created.version,
    });

    await page.getByLabel('Name', { exact: true }).fill(`${year.name} mine`);
    await page.getByRole('button', { name: 'Save' }).click();

    await expect(page.getByTestId('latest-values')).toContainText(`${year.name} other`);
    await page.getByRole('button', { name: 'Use the latest values' }).click();
    await expect(page.getByLabel('Name', { exact: true })).toHaveValue(`${year.name} other`);
  });
});
