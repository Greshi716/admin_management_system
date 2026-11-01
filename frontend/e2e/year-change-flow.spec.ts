import { expect, Page, test } from '@playwright/test';
import { clearFees, getFeeStructure } from './support/fees';
import { callApi, clearMasterData } from './support/master-data';
import { clearReceipts, getFeeCollection } from './support/receipts';
import { studentInput } from './support/students';
import { signInAsTestAdmin } from './support/test-data';

/**
 * Year change end-to-end integration flow (FR-020, FR-023, FR-027):
 * Runs the whole story:
 * 1. Setup old year (2025-26) with Std 1 and Std 2, division A each, finalised structure with 12,000 fee for Std 1.
 * 2. Year copy: create 2026-27 with "Copy from last year".
 * 3. Configure Std 2 fee in 2026-27 (15,000 tuition + 2,000 registration) and finalise structure.
 * 4. Create student in 2026-27, move record to 2025-26, seed 9,000 payment (3,000 remains unpaid).
 * 5. Promote Std 1 on /yearchange/promote/${std1.id}: confirm promotion with 3,000.00 carried forward.
 * 6. Check student profile: 3,000.00 brought forward with "brought forward" badge listed first.
 * 7. Open fee collection: verify 3,000.00 brought forward with badge and earlier-first banner.
 * 8. Pay 3,000.00 earlier years fee first.
 * 9. Pay new year fee: partial registration fee is rejected with REGISTRATION_PART_PAYMENT; exact registration fee is accepted.
 */
test.describe('year change flow', () => {
  test.beforeEach(async ({ page }) => {
    await signInAsTestAdmin(page);
    await clearReceipts(page);
    await clearFees(page);
    await clearMasterData(page, { currentYear: false });
  });

  test.afterEach(async ({ page }) => {
    await clearReceipts(page);
  });

  test('FR-020, FR-023, FR-027: year copy, finalise, promote with part-paid student, check brought forward badge, pay earlier first, registration part payment check', async ({
    page,
  }) => {
    test.setTimeout(60000);

    // 1. Setup old year (2025-26)
    const oldYear = (
      await callApi(page, 'POST', '/api/academic-years', {
        name: '2025-26',
        startDate: '2025-04-01',
        endDate: '2026-03-31',
      })
    ).body;

    const std1 = (
      await callApi(page, 'POST', '/api/standards', {
        name: 'Standard 1',
        displayOrder: 1,
      })
    ).body;

    const std2 = (
      await callApi(page, 'POST', '/api/standards', {
        name: 'Standard 2',
        displayOrder: 2,
      })
    ).body;

    const div1A = (
      await callApi(page, 'POST', '/api/divisions', {
        academicYearId: oldYear.id,
        standardId: std1.id,
        name: 'A',
      })
    ).body;

    const div2A = (
      await callApi(page, 'POST', '/api/divisions', {
        academicYearId: oldYear.id,
        standardId: std2.id,
        name: 'A',
      })
    ).body;

    const oldStructure = (await callApi(page, 'GET', `/api/fee-structures/${oldYear.id}`)).body;
    await callApi(page, 'POST', `/api/fee-structures/${oldYear.id}/rows`, {
      standardId: std1.id,
      amount: 12000,
      registrationFee: null,
      dueDate: '2025-07-15',
      structureVersion: oldStructure.version,
    });

    const structBeforeFinalise = (await callApi(page, 'GET', `/api/fee-structures/${oldYear.id}`)).body;
    await callApi(page, 'POST', `/api/fee-structures/${oldYear.id}/finalise`, {
      version: structBeforeFinalise.version,
    });

    const categories = (
      await callApi(page, 'POST', '/api/student-categories/search', {
        first: 0,
        rows: 10,
        filters: {},
      })
    ).body.rows;
    const catGeneral = categories[0];

    // 2. Year Copy: Add new academic year 2026-27 with Copy from last year
    await page.goto('/setup/academic-years');
    await expect(page.getByRole('heading', { name: 'Academic years' })).toBeVisible();

    const dialog = page.getByRole('dialog', { name: 'Add academic year' });
    if (!(await dialog.isVisible())) {
      await page.getByRole('button', { name: 'Add academic year' }).click();
    }
    await expect(dialog).toBeVisible();

    await page.getByLabel('Name', { exact: true }).fill('2026-27');
    await page.getByLabel('Start date', { exact: true }).fill('2026-04-01');
    await page.getByLabel('End date', { exact: true }).fill('2027-03-31');

    const copyCheckbox = page.getByLabel('Copy from last year');
    await expect(copyCheckbox).toBeVisible();
    await copyCheckbox.check();

    await page.getByRole('button', { name: 'Save' }).click();

    // Verify copy result message
    const copyResult = page.getByTestId('copy-result');
    await expect(copyResult).toBeVisible();
    await expect(copyResult).toContainText('Copied 2 divisions and 1 fee amount');

    // 3. Configure new year fee structure and finalise
    const years = (await callApi(page, 'GET', '/api/academic-years')).body;
    const newYear = years.find((y: { name: string }) => y.name === '2026-27');
    expect(newYear).toBeDefined();

    // Add Standard 2 fee row and finalise 2026-27
    await callApi(page, 'POST', `/api/fee-structures/${newYear.id}/rows`, {
      standardId: std2.id,
      amount: 15000,
      registrationFee: 2000,
      dueDate: '2026-07-15',
      structureVersion: (await callApi(page, 'GET', `/api/fee-structures/${newYear.id}`)).body.version,
    });
    const newStructFin = (await callApi(page, 'GET', `/api/fee-structures/${newYear.id}`)).body;
    await callApi(page, 'POST', `/api/fee-structures/${newYear.id}/finalise`, {
      version: newStructFin.version,
    });

    // 4. Create student in newYear and move to oldYear (seed 9000 out of 12000 paid -> 3000 unpaid)
    const newDivs = (await callApi(page, 'POST', '/api/divisions/search', { first: 0, rows: 100, filters: {} })).body.rows;
    const newDiv1A = newDivs.find(
      (d: { academicYearId: number; standardId: number; name: string }) =>
        d.academicYearId === newYear.id && d.standardId === std1.id && d.name === 'A',
    );

    const master = {
      yearId: newYear.id,
      standardId: std1.id,
      divisionId: newDiv1A.id,
      categoryId: catGeneral.id,
    };
    const student = (await callApi(page, 'POST', '/api/students', studentInput(701, master))).body;

    await callApi(
      page,
      'POST',
      `/api/test-support/students/${student.id}/move-record?academicYearId=${oldYear.id}&divisionId=${div1A.id}&yearStartDate=${oldYear.startDate}`,
    );

    await callApi(
      page,
      'POST',
      `/api/test-support/seed-receipts?academicYearId=${oldYear.id}&count=1&receiptDate=2025-06-01&feePaid=9000.00&prefix=E2E-R701`,
    );

    // 5. Promote Std 1 into Std 2
    await page.goto(`/yearchange/promote/${std1.id}`);
    await expect(page.getByTestId('promote-title')).toBeVisible();

    const rows = page.getByTestId('student-row');
    await expect(rows).toHaveCount(1);
    await expect(page.getByTestId('promote-tick').first()).toBeChecked();
    await expect(page.getByTestId('unpaid-fee').first()).toContainText('3000.00');

    // Save promotion & confirmation dialog
    await page.getByTestId('save-promote').click();
    await expect(page.getByTestId('promote-count')).toHaveText('1');
    await expect(page.getByTestId('carried-total')).toContainText('3,000.00');

    await page.getByTestId('confirm-promote').click();
    await expect(page.getByTestId('promote-result-summary')).toBeVisible();
    await expect(page.getByTestId('result-promoted')).toHaveText('1');
    await expect(page.getByTestId('result-carried')).toContainText('3000.00');

    // 6. Check student profile: brought forward is listed first with badge
    await page.goto(`/students/${student.id}`);
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Testsurname701');

    const bfBadge = page.getByTestId('brought-forward-badge');
    await expect(bfBadge).toBeVisible();
    await expect(bfBadge).toHaveText('brought forward');
    await expect(page.getByTestId('fee-brought-forward')).toContainText('3,000.00');

    // 7. Fee collection: verify brought forward badge and earlier-first banner
    await page.goto(`/finance/fee-collection?studentId=${student.id}`);
    await expect(page.getByTestId('strip')).toBeVisible();
    await expect(page.getByTestId('strip').getByTestId('brought-forward-badge')).toBeVisible();
    await expect(page.getByTestId('earlier-first')).toBeVisible();
    await expect(page.getByTestId('earlier-first').getByTestId('brought-forward-badge')).toBeVisible();
    await expect(page.getByTestId('earlier-first')).toContainText('3,000.00');

    // 8. Pay 3,000.00 earlier years fee first
    await expect(page.getByLabel('Receipt type')).toHaveValue('PREVIOUS_YEARS');
    await page.getByLabel('Receipt no. (from the book)').fill('E2E-BF701');
    await page.getByLabel("Earlier years' fee").fill('3000');
    await page.getByTestId('save').click();

    await expect(page.getByTestId('confirm-type')).toContainText('Previous years');
    await expect(page.getByTestId('confirm-total')).toHaveText('3,000.00');
    await page.getByRole('button', { name: 'Confirm and save' }).click();
    await expect(page).toHaveURL(/\/finance\/receipts\/\d+$/);

    // 9. Return to fee collection: earlier years cleared, test REGISTRATION_PART_PAYMENT error
    await page.goto(`/finance/fee-collection?studentId=${student.id}`);
    await expect(page.getByTestId('strip')).toBeVisible();
    await expect(page.getByTestId('earlier-first')).toHaveCount(0);
    await expect(page.getByLabel('Receipt type')).toHaveValue('CURRENT_YEAR');

    // Try paying partial registration fee (500 out of 2000)
    await page.getByLabel('Receipt no. (from the book)').fill('E2E-CY701');
    await page.getByLabel('Registration fee').fill('500');
    await page.getByLabel('Year fee').fill('1000');
    await page.getByTestId('save').click();

    // Verify REGISTRATION_PART_PAYMENT error is shown
    await expect(page.getByTestId('form-note')).toContainText('registration fee of 2000.00 must be paid in full');
    await expect(page.getByTestId('confirm-details')).toHaveCount(0);

    // Now pay registration fee in full (2000.00)
    await page.getByLabel('Registration fee').fill('2000');
    await page.getByTestId('save').click();

    await expect(page.getByTestId('confirm-type')).toContainText('Current year');
    await expect(page.getByTestId('confirm-total')).toHaveText('3,000.00');
    await page.getByRole('button', { name: 'Confirm and save' }).click();
    await expect(page).toHaveURL(/\/finance\/receipts\/\d+$/);

    // Verify balance after payments
    const finalCollection = await getFeeCollection(page, student.id);
    expect(finalCollection.balance['previousYearsPaid']).toBe(3000);
    expect(finalCollection.balance['earlierYearsPending']).toBe(0);
    expect(finalCollection.balance['registrationDue']).toBe(0);
    expect(finalCollection.balance['paid']).toBe(6000);
    expect(finalCollection.balance['yearFeeDue']).toBe(14000);
  });
});
