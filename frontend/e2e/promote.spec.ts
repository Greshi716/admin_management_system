import { expect, test } from '@playwright/test';
import { clearFees } from './support/fees';
import { callApi, clearMasterData } from './support/master-data';
import { studentInput } from './support/students';
import { signInAsTestAdmin } from './support/test-data';

/**
 * Promote students page e2e tests (FR-006 to FR-012, FR-019):
 * Table of students all ticked, select-all and untick-all, division/category pickers,
 * Left action, bulk stream choice for Std 10 to 11, Std 12 has no promote tick,
 * unpaid year fee, confirmation dialog with counts, result summary, and hidden LC link.
 */
test.describe('promote students page', () => {
  test.beforeEach(async ({ page }) => {
    await signInAsTestAdmin(page);
    await clearFees(page);
    await clearMasterData(page, { currentYear: false });
  });

  test('FR-006, FR-007, FR-008, FR-009, FR-010, FR-011, FR-012, FR-019: promote standard flow, bulk stream, dialog counts, and Std 12', async ({
    page,
  }) => {
    // 1. Setup old year (2025-26) and new current year (2026-27)
    const oldYear = (
      await callApi(page, 'POST', '/api/academic-years', {
        name: '2025-26',
        startDate: '2025-04-01',
        endDate: '2026-03-31',
      })
    ).body;

    const newYear = (
      await callApi(page, 'POST', '/api/academic-years', {
        name: '2026-27',
        startDate: '2026-04-01',
        endDate: '2027-03-31',
      })
    ).body;

    // Standards: Std 10, Std 11, Std 12
    const std10 = (
      await callApi(page, 'POST', '/api/standards', {
        name: 'Standard 10',
        displayOrder: 10,
      })
    ).body;

    const std11 = (
      await callApi(page, 'POST', '/api/standards', {
        name: 'Standard 11',
        displayOrder: 11,
      })
    ).body;

    const std12 = (
      await callApi(page, 'POST', '/api/standards', {
        name: 'Standard 12',
        displayOrder: 12,
      })
    ).body;

    // Divisions
    const oldDiv10A = (
      await callApi(page, 'POST', '/api/divisions', {
        academicYearId: oldYear.id,
        standardId: std10.id,
        name: 'A',
      })
    ).body;

    await callApi(page, 'POST', '/api/divisions', {
      academicYearId: newYear.id,
      standardId: std10.id,
      name: 'A',
    });

    const newDiv11A = (
      await callApi(page, 'POST', '/api/divisions', {
        academicYearId: newYear.id,
        standardId: std11.id,
        name: 'A',
      })
    ).body;

    await callApi(page, 'POST', '/api/divisions', {
      academicYearId: newYear.id,
      standardId: std11.id,
      name: 'B',
    });

    await callApi(page, 'POST', '/api/divisions', {
      academicYearId: oldYear.id,
      standardId: std12.id,
      name: 'A',
    });

    await callApi(page, 'POST', '/api/divisions', {
      academicYearId: newYear.id,
      standardId: std12.id,
      name: 'A',
    });

    const categories = (
      await callApi(page, 'POST', '/api/student-categories/search', {
        first: 0,
        rows: 10,
        filters: {},
      })
    ).body.rows;
    const catGeneral = categories[0];

    // Finalise fee structure in new year for Std 10 and Std 11
    const newStructure = (await callApi(page, 'GET', `/api/fee-structures/${newYear.id}`)).body;
    await callApi(page, 'POST', `/api/fee-structures/${newYear.id}/rows`, {
      standardId: std10.id,
      amount: 15000,
      registrationFee: null,
      dueDate: '2026-07-15',
      structureVersion: newStructure.version,
    });
    const s2 = (await callApi(page, 'GET', `/api/fee-structures/${newYear.id}`)).body;
    await callApi(page, 'POST', `/api/fee-structures/${newYear.id}/rows`, {
      standardId: std11.id,
      amount: 20000,
      registrationFee: null,
      dueDate: '2026-07-15',
      structureVersion: s2.version,
    });
    const s3 = (await callApi(page, 'GET', `/api/fee-structures/${newYear.id}`)).body;
    await callApi(page, 'POST', `/api/fee-structures/${newYear.id}/rows`, {
      standardId: std12.id,
      amount: 25000,
      registrationFee: null,
      dueDate: '2026-07-15',
      structureVersion: s3.version,
    });
    const sFin = (await callApi(page, 'GET', `/api/fee-structures/${newYear.id}`)).body;
    await callApi(page, 'POST', `/api/fee-structures/${newYear.id}/finalise`, {
      version: sFin.version,
    });

    // Old fee structure
    const oldStruct = (await callApi(page, 'GET', `/api/fee-structures/${oldYear.id}`)).body;
    await callApi(page, 'POST', `/api/fee-structures/${oldYear.id}/rows`, {
      standardId: std10.id,
      amount: 12000,
      registrationFee: null,
      dueDate: '2025-07-15',
      structureVersion: oldStruct.version,
    });
    const oldFin = (await callApi(page, 'GET', `/api/fee-structures/${oldYear.id}`)).body;
    await callApi(page, 'POST', `/api/fee-structures/${oldYear.id}/finalise`, {
      version: oldFin.version,
    });

    // Create 3 students in old year Std 10
    const master = {
      yearId: oldYear.id,
      standardId: std10.id,
      divisionId: oldDiv10A.id,
      categoryId: catGeneral.id,
    };

    const s1 = (await callApi(page, 'POST', '/api/students', studentInput(501, master))).body;
    const s2Obj = (await callApi(page, 'POST', '/api/students', studentInput(502, master))).body;
    const s3Obj = (await callApi(page, 'POST', '/api/students', studentInput(503, master))).body;

    // Create part receipt for s1 (paid 9000 out of 12000 -> 3000 unpaid)
    const token = (await callApi(page, 'GET', `/api/students/${s1.id}/fee-collection`)).body.balanceVersion;
    await callApi(page, 'POST', '/api/receipts', {
      studentId: s1.id,
      balanceVersion: token,
      receiptType: 'CURRENT_YEAR',
      receiptNo: 'E2E-R501',
      receiptDate: '2025-06-01',
      feePaid: 9000,
      paymentMode: 'CASH',
    });

    // 2. Open promote screen for Std 10
    await page.goto(`/yearchange/promote/${std10.id}`);
    await expect(page.getByTestId('promote-title')).toBeVisible();

    // Verify all 3 students shown, ticked by default
    const rows = page.getByTestId('student-row');
    await expect(rows).toHaveCount(3);
    const ticks = page.getByTestId('promote-tick');
    await expect(ticks.nth(0)).toBeChecked();
    await expect(ticks.nth(1)).toBeChecked();
    await expect(ticks.nth(2)).toBeChecked();

    // Unpaid fee for s1 is 3,000.00
    await expect(page.getByTestId('unpaid-fee').nth(0)).toContainText('3000.00');

    // 3. Select all / untick all
    await page.getByTestId('untick-all').click();
    await expect(ticks.nth(0)).not.toBeChecked();
    await expect(ticks.nth(1)).not.toBeChecked();
    await expect(ticks.nth(2)).not.toBeChecked();

    await page.getByTestId('select-all').click();
    await expect(ticks.nth(0)).toBeChecked();
    await expect(ticks.nth(1)).toBeChecked();
    await expect(ticks.nth(2)).toBeChecked();

    // 4. Bulk stream choice (Std 10 into Std 11 uses stream)
    await expect(page.getByTestId('bulk-stream-bar')).toBeVisible();
    await page.getByTestId('bulk-stream').selectOption('Science');
    await page.getByTestId('apply-bulk-stream').click();

    // 5. Customise student rows:
    // s2: untick -> Detained
    await ticks.nth(1).uncheck();

    // s3: Mark Left
    await page.getByTestId('mark-left-btn').nth(2).click();
    await expect(page.getByTestId('mark-left-btn').nth(2)).toHaveText('Undo Left');

    // LC link is hidden
    await expect(page.getByTestId('lc-link').nth(0)).toBeHidden();

    // 6. Save promotion & Confirmation dialog
    await page.getByTestId('save-promote').click();

    // Confirm dialog shows counts: 1 Promoted, 1 Stayed (Detained), 1 Left, carried total 3000.00
    await expect(page.getByTestId('promote-count')).toHaveText('1');
    await expect(page.getByTestId('detained-count')).toHaveText('1');
    await expect(page.getByTestId('left-count')).toHaveText('1');
    await expect(page.getByTestId('carried-total')).toContainText('3,000.00');

    // Confirm
    await page.getByTestId('confirm-promote').click();

    // 7. Result summary displayed
    await expect(page.getByTestId('promote-result-summary')).toBeVisible();
    await expect(page.getByTestId('result-promoted')).toHaveText('1');
    await expect(page.getByTestId('result-detained')).toHaveText('1');
    await expect(page.getByTestId('result-left')).toHaveText('1');
    await expect(page.getByTestId('result-carried')).toContainText('3000.00');

    // 8. Std 12 promote screen: create 1 student in Std 12 old year
    const s12Master = {
      yearId: oldYear.id,
      standardId: std12.id,
      divisionId: (await callApi(page, 'GET', `/api/academic-years/${oldYear.id}/divisions`)).body[0]?.id || 1,
      categoryId: catGeneral.id,
    };
    // Fetch divisions of std12 in old year
    const div12Old = (
      await callApi(page, 'POST', '/api/divisions', {
        academicYearId: oldYear.id,
        standardId: std12.id,
        name: 'B',
      })
    ).body;
    s12Master.divisionId = div12Old.id;

    await callApi(page, 'POST', '/api/students', studentInput(512, s12Master));

    await page.goto(`/yearchange/promote/${std12.id}`);
    await expect(page.getByTestId('promote-title')).toBeVisible();

    // In Std 12: no promote tick exists
    await expect(page.getByTestId('promote-tick')).toHaveCount(0);
    // Student defaults to Detained status badge
    await expect(page.getByTestId('row-status-badge').first()).toHaveText('Detained');
    // Clicking Mark Left changes badge to Left
    await page.getByTestId('mark-left-btn').first().click();
    await expect(page.getByTestId('row-status-badge').first()).toHaveText('Left');
  });
});
