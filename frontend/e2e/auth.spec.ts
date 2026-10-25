import { expect, Page, test } from '@playwright/test';
import { ensureCurrentYearThenSignOut } from './support/master-data';
import { resetTestAdmin, TEST_ADMIN } from './support/test-data';

const WRONG_MESSAGE = 'The username or password is not correct.';

async function signInThroughTheForm(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/sign-in');
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
}

test.describe('sign-in and sign-out', () => {
  test.beforeEach(async ({ page }) => {
    await resetTestAdmin(page); // first: it also lifts a lock-out left by an earlier test
    await ensureCurrentYearThenSignOut(page); // with no current year the app opens Academic years, not the home page
  });

  test('FR-007: a screen address without a session shows the sign-in page', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL(/\/sign-in$/);
    await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();

    await page.goto('/some/unknown/screen');
    await expect(page).toHaveURL(/\/sign-in$/);
  });

  test('FR-001: the correct username and password lead to the home page', async ({ page }) => {
    await signInThroughTheForm(page, TEST_ADMIN.username, TEST_ADMIN.password);

    await expect(page).toHaveURL(/\/$/);
    await expect(page.getByTestId('welcome')).toContainText('Welcome');
  });

  test('FR-004: a wrong password and a wrong username show the same message', async ({ page }) => {
    await signInThroughTheForm(page, TEST_ADMIN.username, 'not-the-password');
    await expect(page.getByTestId('wrong-credentials')).toHaveText(WRONG_MESSAGE);

    await signInThroughTheForm(page, 'nobody-here', 'not-the-password');
    await expect(page.getByTestId('wrong-credentials')).toHaveText(WRONG_MESSAGE);
    await expect(page).toHaveURL(/\/sign-in$/);
  });

  test('FR-044: five wrong passwords pause sign-in and say when to try again', async ({ page }) => {
    await page.goto('/sign-in');
    for (let attempt = 1; attempt <= 5; attempt++) {
      await page.getByLabel('Username').fill(TEST_ADMIN.username);
      await page.getByLabel('Password', { exact: true }).fill('not-the-password');
      // Wait for each answer: the notice of the previous attempt is still on screen, so it cannot be the signal.
      const answer = page.waitForResponse((response) => response.url().endsWith('/api/auth/login'));
      await page.getByRole('button', { name: 'Sign in' }).click();
      await answer;
    }

    const notice = page.getByTestId('paused-notice');
    await expect(notice).toContainText('Try again in 5 minutes');
    await expect(notice).toContainText('You can try again at');
    await expect(page.getByRole('button', { name: 'Sign in' })).toBeDisabled();
  });

  test('FR-006: logging out ends the session, also for the back button', async ({ page }) => {
    await signInThroughTheForm(page, TEST_ADMIN.username, TEST_ADMIN.password);
    await expect(page.getByTestId('welcome')).toBeVisible();

    await page.getByRole('button', { name: 'Log out' }).click();
    await expect(page).toHaveURL(/\/sign-in$/);

    await page.goBack();
    await expect(page).toHaveURL(/\/sign-in$/);
    await page.goto('/');
    await expect(page).toHaveURL(/\/sign-in$/);
    expect((await page.context().request.get('/api/auth/me')).status()).toBe(401);
  });

  test('FR-008: when the session is gone the next action leads to sign-in with a message', async ({ page }) => {
    await signInThroughTheForm(page, TEST_ADMIN.username, TEST_ADMIN.password);
    await expect(page.getByTestId('welcome')).toBeVisible();

    // The server forgets the session after 10 idle minutes; dropping its cookie has the same effect.
    await page.context().clearCookies({ name: 'JSESSIONID' });
    await page.getByRole('button', { name: 'Change password' }).first().click();
    await page.getByLabel('Current password').fill(TEST_ADMIN.password);
    await page.getByLabel('New password', { exact: true }).fill('Another-Password-9');
    await page.getByLabel('Confirm new password').fill('Another-Password-9');
    await page.getByRole('button', { name: 'Change password' }).last().click();

    await expect(page).toHaveURL(/\/sign-in$/);
    await expect(page.getByTestId('session-notice')).toContainText('10 minutes without activity');
  });
});

test.describe('change-password popup', () => {
  test.beforeEach(async ({ page }) => {
    await resetTestAdmin(page, { passwordChanged: false });
    await ensureCurrentYearThenSignOut(page);
  });

  test.afterEach(async ({ page }) => {
    await resetTestAdmin(page);
  });

  test('FR-009: "Later" closes the popup and it comes back at the next sign-in', async ({ page }) => {
    await signInThroughTheForm(page, TEST_ADMIN.username, TEST_ADMIN.password);
    const popup = page.getByRole('dialog', { name: 'Change your password' });
    await expect(popup).toBeVisible();

    await popup.getByRole('button', { name: 'Later' }).click();
    await expect(popup).toBeHidden();
    await expect(page.getByTestId('welcome')).toBeVisible();

    await page.reload();
    await expect(page.getByRole('dialog', { name: 'Change your password' })).toBeVisible();
  });

  test('FR-009: "Change now" changes the password and the popup stops appearing', async ({ page }) => {
    const newPassword = 'Brand-New-Password-7';
    await signInThroughTheForm(page, TEST_ADMIN.username, TEST_ADMIN.password);

    await page.getByRole('dialog', { name: 'Change your password' }).getByRole('button', { name: 'Change now' }).click();
    const form = page.getByRole('dialog', { name: 'Change password' });
    await form.getByLabel('Current password').fill(TEST_ADMIN.password);
    await form.getByLabel('New password', { exact: true }).fill('short');
    await form.getByLabel('Confirm new password').fill('short');
    await form.getByRole('button', { name: 'Change password' }).click();
    await expect(form).toContainText('Use at least 8 characters.');

    await form.getByLabel('New password', { exact: true }).fill(newPassword);
    await form.getByLabel('Confirm new password').fill(newPassword);
    await form.getByRole('button', { name: 'Change password' }).click();
    await expect(form).toBeHidden();

    await page.reload();
    await expect(page.getByTestId('welcome')).toBeVisible();
    await expect(page.getByRole('dialog', { name: 'Change your password' })).toBeHidden();
    await page.getByRole('button', { name: 'Log out' }).click();
    await signInThroughTheForm(page, TEST_ADMIN.username, newPassword);
    await expect(page.getByTestId('welcome')).toBeVisible();
  });

  test('FR-009: the wrong current password is shown beside the field', async ({ page }) => {
    await signInThroughTheForm(page, TEST_ADMIN.username, TEST_ADMIN.password);
    await page.getByRole('dialog', { name: 'Change your password' }).getByRole('button', { name: 'Change now' }).click();

    const form = page.getByRole('dialog', { name: 'Change password' });
    await form.getByLabel('Current password').fill('definitely-wrong');
    await form.getByLabel('New password', { exact: true }).fill('Brand-New-Password-7');
    await form.getByLabel('Confirm new password').fill('Brand-New-Password-7');
    await form.getByRole('button', { name: 'Change password' }).click();

    await expect(form).toContainText('The current password is not correct.');
  });
});
