import { expect, test } from '@playwright/test';
import { clearFees } from './support/fees';
import { callApi, clearMasterData } from './support/master-data';
import { studentInput } from './support/students';
import { signInAsTestAdmin } from './support/test-data';

/**
 * Promotion warning bar and Promote students button e2e tests (FR-006, FR-014, FR-015):
 * 1. Warning bar absent before new year starts.
 * 2. Warning bar appears on main screens when new year has started and standards are pending.
 * 3. "Promote students" button on Students page enabled for pending standard, disabled ("Done") when finished.
 * 4. Warning bar disappears after every standard is promoted.
 */
test.describe('promotion warning bar and promote button', () => {
  test.beforeEach(async ({ page }) => {
    await signInAsTestAdmin(page);
    await clearFees(page);
    await clearMasterData(page, { currentYear: false });
  });

  test('FR-006, FR-014, FR-015: warning bar appears when promotion pending, button enabled/done, bar disappears after promotion', async ({
    page,
  }) => {
    // 1. Before new year starts: only one year exists
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

    const div1Old = (
      await callApi(page, 'POST', '/api/divisions', {
        academicYearId: oldYear.id,
        standardId: std1.id,
        name: 'A',
      })
    ).body;

    // Check warning bar is absent
    await page.goto('/students');
    await expect(page.getByTestId('promote-warning-bar')).toHaveCount(0);

    // 2. Now start new year (2026-27 covering today)
    const newYear = (
      await callApi(page, 'POST', '/api/academic-years', {
        name: '2026-27',
        startDate: '2026-04-01',
        endDate: '2027-03-31',
      })
    ).body;

    const std2 = (
      await callApi(page, 'POST', '/api/standards', {
        name: 'Standard 2',
        displayOrder: 2,
      })
    ).body;

    const div1New = (
      await callApi(page, 'POST', '/api/divisions', {
        academicYearId: newYear.id,
        standardId: std1.id,
        name: 'A',
      })
    ).body;

    await callApi(page, 'POST', '/api/divisions', {
      academicYearId: newYear.id,
      standardId: std2.id,
      name: 'A',
    });

    const categories = (
      await callApi(page, 'POST', '/api/student-categories/search', {
        first: 0,
        rows: 10,
        filters: {},
      })
    ).body.rows;

    // Finalise new fee structure
    const newStruct = (await callApi(page, 'GET', `/api/fee-structures/${newYear.id}`)).body;
    await callApi(page, 'POST', `/api/fee-structures/${newYear.id}/rows`, {
      standardId: std1.id,
      amount: 15000,
      registrationFee: null,
      dueDate: '2026-07-15',
      structureVersion: newStruct.version,
    });
    const s2 = (await callApi(page, 'GET', `/api/fee-structures/${newYear.id}`)).body;
    await callApi(page, 'POST', `/api/fee-structures/${newYear.id}/rows`, {
      standardId: std2.id,
      amount: 20000,
      registrationFee: null,
      dueDate: '2026-07-15',
      structureVersion: s2.version,
    });
    const sFin = (await callApi(page, 'GET', `/api/fee-structures/${newYear.id}`)).body;
    await callApi(page, 'POST', `/api/fee-structures/${newYear.id}/finalise`, {
      version: sFin.version,
    });

    // Add a student in old year Std 1
    const master = {
      yearId: newYear.id,
      standardId: std1.id,
      divisionId: div1New.id,
      categoryId: categories[0].id,
    };
    const student = (await callApi(page, 'POST', '/api/students', studentInput(601, master))).body;
    expect(student?.id).toBeTruthy();
    await callApi(
      page,
      'POST',
      `/api/test-support/students/${student.id}/move-record?academicYearId=${oldYear.id}&divisionId=${div1Old.id}&yearStartDate=${oldYear.startDate}`,
    );

    // 3. Reload Students screen -> Warning bar MUST now appear
    await page.goto('/students');
    await expect(page.getByTestId('promote-warning-bar')).toBeVisible();
    await expect(page.getByTestId('pending-standards')).toContainText('Standard 1');
    await expect(page.getByTestId('promote-warning-link')).toBeVisible();

    // Check button on standard filter
    await page.getByTestId('standard-filter').selectOption(String(std1.id));
    const btn = page.getByTestId('promote-students-btn');
    await expect(btn).toBeEnabled();
    await expect(btn).toHaveText('Promote students');

    // Clicking the promote link or button opens promote page
    await btn.click();
    await expect(page.getByTestId('promote-title')).toBeVisible();

    // Save promotion for Std 1
    await page.getByTestId('save-promote').click();
    await page.getByTestId('confirm-promote').click();
    await expect(page.getByTestId('promote-result-summary')).toBeVisible();

    // 4. Return to Students page -> Warning bar must now disappear because all standards are done
    await page.goto('/students');
    await expect(page.getByTestId('promote-warning-bar')).toHaveCount(0);

    // Standard filter for Std 1 now shows Done
    await page.getByTestId('standard-filter').selectOption(String(std1.id));
    await expect(page.getByTestId('promote-students-btn')).toBeDisabled();
    await expect(page.getByTestId('promote-students-btn')).toHaveText('Done');
  });
});
