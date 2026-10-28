import { expect, test } from '@playwright/test';
import { callApi, clearMasterData, ensureCurrentYear } from './support/master-data';
import { academicYearAround, signInAsTestAdmin } from './support/test-data';

// Synthetic values only (constitution VI). Test plan FR-013-B1 and FR-013-N2.

test.describe('fee structure in Setup', () => {
  test.beforeEach(async ({ page }) => {
    await signInAsTestAdmin(page);
    await clearMasterData(page);
  });

  test('FR-013: Setup lists Fee structure and none of the prototype parts that are not built', async ({ page }) => {
    await page.goto('/setup/fees');
    const nav = page.getByRole('navigation', { name: 'Setup' });

    await expect(nav.getByRole('link', { name: 'Fee structure' })).toBeVisible();
    for (const hidden of [
      'Copy from last year',
      'Salary rules',
      'Facilities and deductions',
      'Leave types',
      'Backup and restore',
      'Excel import',
    ]) {
      await expect(nav.getByText(hidden), `${hidden} is not offered`).toHaveCount(0);
    }
  });

  test('FR-013: the year picker starts on the current year and another year can be opened', async ({ page }) => {
    const current = await ensureCurrentYear(page);
    const next = (await callApi(page, 'POST', '/api/academic-years', academicYearAround(new Date(), 1))).body;

    await page.goto('/setup/fees');
    const picker = page.getByLabel('Academic year');
    await expect(picker).toHaveValue(String(current.id));
    await expect(page.getByTestId('fee-card-title')).toContainText(current.name);

    await picker.selectOption({ label: next.name });

    await expect(page.getByTestId('fee-card-title')).toContainText(next.name);
    await expect(page.getByTestId('fee-status')).toHaveText('Draft');
    await expect(page.getByRole('button', { name: 'Add row' })).toBeVisible();
  });
});
