import { expect, Locator, Page, test } from '@playwright/test';
import { clearFees, getFeeStructure } from './support/fees';
import { callApi, clearMasterData } from './support/master-data';
import { signInAsTestAdmin } from './support/test-data';

/**
 * Year copy e2e tests (FR-001, FR-003, FR-004):
 * Creates a year with copy from last year, edits one copied fee row,
 * finalises the new year, and verifies the old year remains untouched.
 */

async function openYears(page: Page): Promise<void> {
  await page.goto('/setup/academic-years');
  await expect(page.getByRole('heading', { name: 'Academic years' })).toBeVisible();
}

const rowAt = (page: Page, index: number): Locator => page.getByTestId('fee-row').nth(index);

test.describe('year copy and fee finalise', () => {
  test.beforeEach(async ({ page }) => {
    await signInAsTestAdmin(page);
    await clearFees(page);
    await clearMasterData(page, { currentYear: false });
  });

  test('FR-001, FR-003, FR-004: create with copy, edit one fee, finalise, old year untouched', async ({ page }) => {
    // 1. Setup old year (2026-27) with a standard, a division, and a finalised fee row
    const oldYear = (
      await callApi(page, 'POST', '/api/academic-years', {
        name: '2026-27',
        startDate: '2026-04-01',
        endDate: '2027-03-31',
      })
    ).body;

    const standard = (
      await callApi(page, 'POST', '/api/standards', {
        name: 'Standard 1',
        displayOrder: 1,
      })
    ).body;

    await callApi(page, 'POST', '/api/divisions', {
      academicYearId: oldYear.id,
      standardId: standard.id,
      name: 'A',
    });

    const oldStructure = (await callApi(page, 'GET', `/api/fee-structures/${oldYear.id}`)).body;
    await callApi(page, 'POST', `/api/fee-structures/${oldYear.id}/rows`, {
      standardId: standard.id,
      amount: 30000,
      registrationFee: 500,
      dueDate: '2026-07-15',
      structureVersion: oldStructure.version,
    });

    const structBeforeFinalise = (await callApi(page, 'GET', `/api/fee-structures/${oldYear.id}`)).body;
    await callApi(page, 'POST', `/api/fee-structures/${oldYear.id}/finalise`, {
      version: structBeforeFinalise.version,
    });

    const oldYearFinal = await getFeeStructure(page, oldYear.id);
    expect(oldYearFinal.status).toBe('Finalised');
    expect(oldYearFinal.rows).toHaveLength(1);
    expect(oldYearFinal.rows[0].amount).toBe(30000);

    // 2. Open Academic Years and create new year (2027-28) with "Copy from last year"
    await openYears(page);
    await page.getByRole('button', { name: 'Add academic year' }).click();
    await expect(page.getByRole('dialog', { name: 'Add academic year' })).toBeVisible();

    await page.getByLabel('Name', { exact: true }).fill('2027-28');
    await page.getByLabel('Start date', { exact: true }).fill('2027-04-01');
    await page.getByLabel('End date', { exact: true }).fill('2028-03-31');

    const copyCheckbox = page.getByLabel('Copy from last year');
    await expect(copyCheckbox).toBeVisible();
    await copyCheckbox.check();

    await page.getByRole('button', { name: 'Save' }).click();

    // 3. Result message with counts and link to Fee structure appears
    const copyResult = page.getByTestId('copy-result');
    await expect(copyResult).toBeVisible();
    await expect(copyResult).toContainText('Copied 1 division and 1 fee amount');

    // 4. Click the link to Fee structure
    await copyResult.getByRole('link', { name: 'Fee structure' }).click();
    await expect(page).toHaveURL(/\/setup\/fees/);
    await expect(page.getByRole('heading', { name: 'Fee structure', exact: true })).toBeVisible();

    // Switch to new year 2027-28 if not already selected
    const yearPicker = page.getByLabel('Academic year');
    const selectedYear = await yearPicker.inputValue();
    if (selectedYear !== String(oldYear.id)) {
      // 2027-28 might already be selected or select it
      await yearPicker.selectOption({ label: '2027-28' });
    } else {
      await yearPicker.selectOption({ label: '2027-28' });
    }

    await expect(page.getByTestId('fee-card-title')).toContainText('2027-28');
    await expect(page.getByTestId('fee-status')).toHaveText('Draft');

    // Verify due date was shifted forward by 1 year (2026-07-15 -> 2027-07-15)
    const feeRow = rowAt(page, 0);
    await expect(feeRow.getByLabel('Due date')).toHaveValue('2027-07-15');
    await expect(feeRow.getByLabel('Yearly fee')).toHaveValue('30000.00');

    // 5. Edit one fee row: change yearly fee to 35000
    await feeRow.getByLabel('Yearly fee').fill('35000');
    await feeRow.getByRole('button', { name: 'Save row' }).click();
    await expect(feeRow.getByLabel('Yearly fee')).toHaveValue('35000.00');

    // 6. Finalise the fee structure in the new year
    await page.getByRole('button', { name: 'Finalise' }).click();
    const finaliseDialog = page.getByRole('dialog', { name: 'Finalise the fee structure' });
    await expect(finaliseDialog).toBeVisible();
    await finaliseDialog.getByRole('button', { name: 'Finalise' }).click();

    await expect(page.getByTestId('fee-status')).toHaveText('Finalised');

    // 7. Verify the old year is completely untouched
    const oldYearCheck = await getFeeStructure(page, oldYear.id);
    expect(oldYearCheck.status).toBe('Finalised');
    expect(oldYearCheck.rows).toHaveLength(1);
    expect(oldYearCheck.rows[0].amount).toBe(30000);
    expect(oldYearCheck.rows[0].dueDate).toBe('2026-07-15');
  });
});
