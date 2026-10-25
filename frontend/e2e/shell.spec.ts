import { expect, Page, test } from '@playwright/test';
import { callApi, clearMasterData } from './support/master-data';
import { academicYearAround, resetTestAdmin, signInAsTestAdmin } from './support/test-data';

const SCREENS = [
  { path: '/', heading: /Welcome/ },
  { path: '/master-data/academic-years', heading: 'Academic years' },
  { path: '/master-data/standards', heading: 'Standards' },
  { path: '/master-data/divisions', heading: 'Divisions' },
  { path: '/master-data/student-categories', heading: 'Student categories' },
  { path: '/audit-log', heading: 'Audit log' },
];

async function createCurrentYear(page: Page): Promise<string> {
  const year = academicYearAround();
  await callApi(page, 'POST', '/api/academic-years', year);
  return year.name;
}

test.describe('app shell', () => {
  test.beforeEach(async ({ page }) => {
    await resetTestAdmin(page, { passwordChanged: true });
    await signInAsTestAdmin(page);
    await clearMasterData(page, { currentYear: false });
  });

  test('FR-028: every screen shows the menu, the user name, the current year, Change password and Log out', async ({
    page,
  }) => {
    const yearName = await createCurrentYear(page);

    for (const screen of SCREENS) {
      await page.goto(screen.path);
      await expect(page.getByRole('heading', { name: screen.heading }).first()).toBeVisible();

      const menu = page.locator('aside nav');
      for (const entry of ['Home', 'Academic years', 'Standards', 'Divisions', 'Student categories', 'Audit log']) {
        await expect(menu.getByRole('link', { name: entry })).toBeVisible();
      }
      await expect(page.getByTestId('user-name')).toHaveText(/\S/);
      await expect(page.getByTestId('current-year')).toContainText(yearName);
      await expect(page.getByRole('button', { name: 'Change password' })).toBeVisible();
      await expect(page.getByRole('button', { name: 'Log out' })).toBeVisible();
    }
  });

  test('FR-028: Log out works from a screen and the next page needs a sign-in again', async ({ page }) => {
    await createCurrentYear(page);
    await page.goto('/master-data/standards');

    await page.getByRole('button', { name: 'Log out' }).click();

    await expect(page).toHaveURL(/\/sign-in$/);
    await page.goto('/master-data/standards');
    await expect(page).toHaveURL(/\/sign-in$/);
  });

  test('FR-013: with no academic year covering today the app opens Academic years with the create form', async ({
    page,
  }) => {
    await page.goto('/');

    await expect(page).toHaveURL(/\/master-data\/academic-years$/);
    await expect(page.getByTestId('no-current-year')).toContainText('No academic year covers today');
    await expect(page.getByRole('dialog', { name: 'Add academic year' })).toBeVisible();
    await expect(page.getByTestId('current-year')).toHaveCount(0);
  });

  test('FR-013: other menu items are disabled, direct links go back, Change password and Log out still work', async ({
    page,
  }) => {
    await page.goto('/');
    await expect(page.getByTestId('no-current-year')).toBeVisible();
    await page.getByRole('button', { name: 'Cancel' }).click();
    const menu = page.locator('aside nav');

    for (const entry of ['Home', 'Standards', 'Divisions', 'Student categories', 'Audit log']) {
      await expect(menu.getByRole('link', { name: entry })).toHaveCount(0);
      await expect(menu.getByText(entry, { exact: true })).toHaveAttribute('aria-disabled', 'true');
    }
    await expect(menu.getByRole('link', { name: 'Academic years' })).toBeVisible();

    await page.goto('/master-data/standards');
    await expect(page).toHaveURL(/\/master-data\/academic-years$/);
    await page.goto('/audit-log');
    await expect(page).toHaveURL(/\/master-data\/academic-years$/);
    // the create form opens again on every visit to Academic years while there is no current year
    await expect(page.getByRole('dialog', { name: 'Add academic year' })).toBeVisible();
    await page.getByRole('button', { name: 'Cancel' }).click();

    await page.getByRole('button', { name: 'Change password' }).click();
    await expect(page.getByRole('dialog', { name: 'Change password' })).toBeVisible();
    await page.getByRole('button', { name: 'Cancel' }).click();

    await page.getByRole('button', { name: 'Log out' }).click();
    await expect(page).toHaveURL(/\/sign-in$/);
  });

  test('FR-013: creating a year that covers today lifts the block at once', async ({ page }) => {
    await page.goto('/');
    const year = academicYearAround();

    await page.getByLabel('Name', { exact: true }).fill(year.name);
    await page.getByLabel('Start date', { exact: true }).fill(year.startDate);
    await page.getByLabel('End date', { exact: true }).fill(year.endDate);
    await page.getByRole('button', { name: 'Save' }).click();

    await expect(page.getByTestId('no-current-year')).toHaveCount(0);
    await expect(page.getByTestId('current-year')).toContainText(year.name);
    await page.locator('aside nav').getByRole('link', { name: 'Standards' }).click();
    await expect(page).toHaveURL(/\/master-data\/standards$/);
  });

  test('FR-013: deleting the only current year brings the block back', async ({ page }) => {
    await createCurrentYear(page);
    await page.goto('/master-data/academic-years');
    await expect(page.getByTestId('current-year')).toBeVisible();

    await page.getByRole('row', { name: /Current/ }).getByRole('button', { name: 'Delete' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Delete' }).click();

    await expect(page.getByTestId('no-current-year')).toBeVisible();
    await expect(page.locator('aside nav').getByText('Standards', { exact: true })).toHaveAttribute(
      'aria-disabled',
      'true',
    );
  });

  test('FR-009: the reminder popup shows after sign-in until the password is changed, Later closes it', async ({
    page,
  }) => {
    await createCurrentYear(page);
    await resetTestAdmin(page, { passwordChanged: false });
    try {
      await signInAsTestAdmin(page);
      await page.goto('/');

      const popup = page.getByRole('dialog', { name: 'Change your password' });
      await expect(popup).toBeVisible();
      await popup.getByRole('button', { name: 'Later' }).click();
      await expect(popup).toBeHidden();

      await page.reload();
      await expect(popup).toBeVisible();
      await popup.getByRole('button', { name: 'Change now' }).click();
      await expect(page.getByRole('dialog', { name: 'Change password' })).toBeVisible();
    } finally {
      await resetTestAdmin(page, { passwordChanged: true });
    }
  });

  test('FR-009: no popup once the password is changed', async ({ page }) => {
    await createCurrentYear(page);

    await page.goto('/');

    await expect(page.getByRole('heading', { name: /Welcome/ })).toBeVisible();
    await expect(page.getByRole('dialog')).toHaveCount(0);
  });

  test('FR-037: moving between screens does not reload the page', async ({ page }) => {
    await createCurrentYear(page);
    await page.goto('/');
    await expect(page.getByRole('heading', { name: /Welcome/ })).toBeVisible();
    await page.evaluate(() => ((window as unknown as { __kept: string }).__kept = 'same page'));
    const menu = page.locator('aside nav');

    await menu.getByRole('link', { name: 'Standards' }).click();
    await expect(page.getByRole('heading', { name: 'Standards' })).toBeVisible();
    await menu.getByRole('link', { name: 'Audit log' }).click();
    await expect(page.getByRole('heading', { name: 'Audit log' })).toBeVisible();

    expect(await page.evaluate(() => (window as unknown as { __kept?: string }).__kept)).toBe('same page');
  });
});
