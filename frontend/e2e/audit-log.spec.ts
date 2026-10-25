import { expect, Page, test } from '@playwright/test';
import { callApi, clearMasterData } from './support/master-data';
import { signInAsTestAdmin } from './support/test-data';

async function openAuditLog(page: Page): Promise<void> {
  await page.goto('/audit-log');
  await expect(page.getByRole('heading', { name: 'Audit log' })).toBeVisible();
}

async function search(page: Page, text: string): Promise<void> {
  await page.getByRole('searchbox', { name: 'Search this list' }).fill(text);
}

test.describe('audit log', () => {
  test.beforeEach(async ({ page }) => {
    await signInAsTestAdmin(page); // writes a Login entry
    await clearMasterData(page);
  });

  test('FR-022: the sign-in is in the log with the Login action under Security', async ({ page }) => {
    await openAuditLog(page);
    await page.getByLabel('Action', { exact: true }).selectOption('Login');
    await page.getByLabel('Area', { exact: true }).selectOption('Security');

    const row = page.getByRole('row').filter({ hasText: 'testadmin' }).first();
    await expect(row).toContainText('Login');
    await expect(row).toContainText('Security');
  });

  test('FR-021: a master data edit shows the old and the new value', async ({ page }) => {
    const created = await callApi(page, 'POST', '/api/standards', { name: 'Audit Std One', displayOrder: 1 });
    await callApi(page, 'PUT', `/api/standards/${created.body.id}`, {
      name: 'Audit Std Two',
      displayOrder: 1,
      version: created.body.version,
    });
    await openAuditLog(page);
    await page.getByLabel('Action', { exact: true }).selectOption('Edit');
    await search(page, `Standard#${created.body.id}`);

    const row = page.getByRole('row').filter({ hasText: `Standard#${created.body.id}` });
    await expect(row).toContainText('Master data');
    await expect(row).toContainText('name: Audit Std One → Audit Std Two');
  });

  test('FR-026: date, month, year, area and action filters narrow the list', async ({ page }) => {
    await callApi(page, 'POST', '/api/standards', { name: 'Audit Filter Std', displayOrder: 7 });
    await openAuditLog(page);
    await search(page, 'Audit Filter Std');
    await expect(page.getByRole('row').filter({ hasText: 'Audit Filter Std' })).toHaveCount(1);

    await page.getByRole('button', { name: 'Today' }).click();
    await expect(page.getByRole('row').filter({ hasText: 'Audit Filter Std' })).toHaveCount(1);

    const now = new Date();
    const month = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
    await page.getByRole('button', { name: 'Clear filters' }).click();
    await page.getByLabel('Month', { exact: true }).fill(month);
    await expect(page.getByRole('row').filter({ hasText: 'Audit Filter Std' })).toHaveCount(1);
    await page.getByLabel('Month', { exact: true }).fill(`${now.getFullYear() - 2}-01`);
    await expect(page.getByText('No rows found')).toBeVisible();

    await page.getByRole('button', { name: 'Clear filters' }).click();
    await page.getByLabel('Year', { exact: true }).fill(String(now.getFullYear()));
    await expect(page.getByRole('row').filter({ hasText: 'Audit Filter Std' })).toHaveCount(1);
    await page.getByLabel('Year', { exact: true }).fill(String(now.getFullYear() - 2));
    await expect(page.getByText('No rows found')).toBeVisible();

    await page.getByRole('button', { name: 'Clear filters' }).click();
    await page.getByLabel('Area', { exact: true }).selectOption('Security');
    await expect(page.getByText('No rows found')).toBeVisible();
    await page.getByLabel('Area', { exact: true }).selectOption('Master data');
    await page.getByLabel('Action', { exact: true }).selectOption('Create');
    await expect(page.getByRole('row').filter({ hasText: 'Audit Filter Std' })).toHaveCount(1);
    await page.getByLabel('Action', { exact: true }).selectOption('Delete');
    await expect(page.getByText('No rows found')).toBeVisible();
  });

  test('FR-026: a start date after the end date shows the message and no rows', async ({ page }) => {
    await openAuditLog(page);
    await page.getByLabel('From', { exact: true }).fill('2026-02-10');
    await page.getByLabel('To', { exact: true }).fill('2026-01-01');

    await expect(page.getByTestId('range-error')).toBeVisible();
    await expect(page.getByText('No rows found')).toBeVisible();
  });

  test('FR-024: the log has no edit or delete control', async ({ page }) => {
    await openAuditLog(page);
    await expect(page.getByRole('row').nth(1)).toBeVisible();

    await expect(page.getByRole('button', { name: /^(Edit|Delete)$/ })).toHaveCount(0);
    await expect(page.getByRole('columnheader', { name: 'Actions' })).toHaveCount(0);
  });
});
