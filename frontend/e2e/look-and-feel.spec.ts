import { expect, Page, test } from '@playwright/test';
import { callApi, clearMasterData } from './support/master-data';
import { resetTestAdmin, signInAsTestAdmin } from './support/test-data';

const ACCENT = 'rgb(109, 74, 224)'; // #6D4AE0
const MIN_CONTROL_HEIGHT = 44;
const MIN_BORDER_CONTRAST = 3;

const SCREENS = [
  '/',
  '/master-data/academic-years',
  '/master-data/standards',
  '/master-data/divisions',
  '/master-data/student-categories',
  '/audit-log',
  '/finance/fee-collection',
  '/finance/receipts',
];

async function signInWithCurrentYear(page: Page): Promise<void> {
  await resetTestAdmin(page, { passwordChanged: true });
  await signInAsTestAdmin(page);
  await clearMasterData(page); // leaves the academic year around today
}

/** Contrast ratio between two CSS colours given as rgb(...) strings (WCAG 2.x). */
async function contrast(page: Page, foreground: string, background: string): Promise<number> {
  return page.evaluate(
    ([fg, bg]) => {
      const channels = (css: string) => (css.match(/[\d.]+/g) ?? []).slice(0, 3).map(Number);
      const luminance = (css: string) => {
        const [r, g, b] = channels(css).map((value) => {
          const s = value / 255;
          return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
        });
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
      };
      const [lighter, darker] = [luminance(fg), luminance(bg)].sort((a, b) => b - a);
      return (lighter + 0.05) / (darker + 0.05);
    },
    [foreground, background],
  );
}

/** Heights of the visible buttons and text inputs on the page, with a name for the failure message. */
async function controlHeights(page: Page): Promise<{ name: string; height: number }[]> {
  return page.evaluate(() =>
    [...document.querySelectorAll<HTMLElement>('button.btn, .input')]
      .filter((element) => element.offsetParent !== null)
      .map((element) => ({
        name: `${element.tagName.toLowerCase()} "${(element.textContent || element.getAttribute('aria-label') || element.getAttribute('placeholder') || '').trim().slice(0, 30)}"`,
        height: element.getBoundingClientRect().height,
      })),
  );
}

test.describe('look and feel', () => {
  test.beforeEach(async ({ page }) => {
    await signInWithCurrentYear(page);
  });

  test('FR-038: a list shows skeleton rows while its data is loading, never a blank screen', async ({ page }) => {
    await page.route('**/api/standards/search', async (route) => {
      await new Promise((resolve) => setTimeout(resolve, 1500));
      await route.continue();
    });

    await page.goto('/master-data/standards');

    await expect(page.getByTestId('skeleton').first()).toBeVisible();
    await expect(page.getByTestId('skeleton')).toHaveCount(0);
    await expect(page.getByText('No rows found')).toBeVisible();
  });

  test('FR-038: the whole page shows a placeholder while the first answer from the server is awaited', async ({
    page,
  }) => {
    await page.route('**/api/auth/me', async (route) => {
      await new Promise((resolve) => setTimeout(resolve, 1500));
      await route.continue();
    });

    await page.goto('/');

    await expect(page.getByTestId('page-skeleton').first()).toBeVisible();
    await expect(page.getByRole('heading', { name: /Welcome/ })).toBeVisible();
    await expect(page.getByTestId('page-skeleton')).toHaveCount(0);
  });

  test('FR-038: a form shows a placeholder while the choices it needs are loading', async ({ page }) => {
    await page.route('**/api/standards', async (route) => {
      if (route.request().method() === 'GET') {
        await new Promise((resolve) => setTimeout(resolve, 1500));
      }
      await route.continue();
    });
    await page.goto('/master-data/divisions');

    await page.getByRole('button', { name: 'Add division' }).click();

    await expect(page.getByRole('dialog').getByTestId('page-skeleton')).toBeVisible();
    await expect(page.getByRole('dialog').getByLabel('Standard')).toBeVisible();
  });

  test('FR-039: saving shows a success message and a refused save shows an error message', async ({ page }) => {
    await page.goto('/master-data/standards');

    await page.getByRole('button', { name: 'Add standard' }).click();
    await page.getByLabel('Name', { exact: true }).fill('Look Std');
    await page.getByLabel('Display order', { exact: true }).fill('1');
    await page.getByRole('button', { name: 'Save' }).click();
    await expect(page.getByRole('status').filter({ hasText: 'The standard was added.' })).toBeVisible();

    await page.getByRole('button', { name: 'Add standard' }).click();
    await page.getByLabel('Name', { exact: true }).fill('Look Std');
    await page.getByLabel('Display order', { exact: true }).fill('2');
    await page.getByRole('button', { name: 'Save' }).click();
    await expect(page.getByRole('status').filter({ hasText: /already|exists|duplicate/i }).first()).toBeVisible();
  });

  test('FR-040: a field shows what to fix while the admin types, before saving', async ({ page }) => {
    await page.goto('/master-data/standards');
    await page.getByRole('button', { name: 'Add standard' }).click();
    const name = page.getByLabel('Name', { exact: true });

    await name.fill('S');
    await expect(page.getByText('Enter the standard name.')).toBeHidden();
    await name.fill('');

    await expect(page.getByText('Enter the standard name.')).toBeVisible();
    await name.fill('Std');
    await expect(page.getByText('Enter the standard name.')).toBeHidden();
  });

  for (const viewport of [
    { name: 'tablet', width: 768, height: 1024 },
    { name: 'desktop', width: 1280, height: 800 },
  ]) {
    test(`FR-041: every screen is usable at ${viewport.name} width (${viewport.width} px)`, async ({ page }) => {
      await page.setViewportSize({ width: viewport.width, height: viewport.height });

      for (const path of SCREENS) {
        await page.goto(path);
        await expect(page.getByRole('heading').first()).toBeVisible();

        const overflow = await page.evaluate(
          () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
        );
        expect(overflow, `${path} scrolls sideways by ${overflow}px`).toBeLessThanOrEqual(0);
        await expect(page.getByRole('button', { name: 'Log out' })).toBeVisible();
        await expect(page.getByRole('button', { name: 'Change password' })).toBeVisible();
        const reachable = await page.locator('nav[aria-label="Main"] a:visible').count();
        expect(reachable, `${path} has no visible menu`).toBeGreaterThan(0);
      }
    });
  }

  test('FR-042: Gujarati text is drawn with the Noto Sans Gujarati font', async ({ page }) => {
    await callApi(page, 'POST', '/api/standards', { name: 'ધોરણ ૧૧ વિજ્ઞાન', displayOrder: 11 });

    await page.goto('/master-data/standards');
    const cell = page.getByRole('cell', { name: 'ધોરણ ૧૧ વિજ્ઞાન' });
    await expect(cell).toBeVisible();

    await expect
      .poll(() =>
        page.evaluate(async () => {
          await document.fonts.ready;
          return [...document.fonts].some(
            (face) => face.family.replace(/["']/g, '') === 'Noto Sans Gujarati' && face.status === 'loaded',
          );
        }),
      )
      .toBe(true);
    const family = await cell.evaluate((element) => getComputedStyle(element).fontFamily);
    expect(family).toContain('Noto Sans Gujarati');
  });

  test('FR-043: every screen uses the one shared theme: accent colour, input border contrast and control height', async ({
    page,
  }) => {
    for (const path of SCREENS) {
      await page.goto(path);
      await expect(page.getByRole('heading').first()).toBeVisible();

      const primary = page.locator('button.btn-primary:visible').first();
      if (await primary.count()) {
        expect(await primary.evaluate((element) => getComputedStyle(element).backgroundColor), `${path} primary button`).toBe(ACCENT);
      }
      const input = page.locator('.input:visible').first();
      if (await input.count()) {
        const { border, background } = await input.evaluate((element) => {
          const style = getComputedStyle(element);
          return { border: style.borderTopColor, background: style.backgroundColor };
        });
        expect(await contrast(page, border, background), `${path} input border contrast`).toBeGreaterThanOrEqual(
          MIN_BORDER_CONTRAST,
        );
      }
      for (const control of await controlHeights(page)) {
        expect(control.height, `${path}: ${control.name}`).toBeGreaterThanOrEqual(MIN_CONTROL_HEIGHT - 0.5);
      }
    }
  });

  test('FR-043: the open dialogs and the sign-in page use the same theme', async ({ page, browser }) => {
    await page.goto('/master-data/standards');
    await page.getByRole('button', { name: 'Add standard' }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    expect(await dialog.getByRole('button', { name: 'Save' }).evaluate((e) => getComputedStyle(e).backgroundColor)).toBe(ACCENT);
    for (const control of await controlHeights(page)) {
      expect(control.height, `dialog: ${control.name}`).toBeGreaterThanOrEqual(MIN_CONTROL_HEIGHT - 0.5);
    }

    const context = await browser.newContext();
    const signedOut = await context.newPage();
    await signedOut.goto('/sign-in');
    const submit = signedOut.getByRole('button', { name: 'Sign in' });
    await expect(submit).toBeVisible();
    expect(await submit.evaluate((e) => getComputedStyle(e).backgroundColor)).toBe(ACCENT);
    for (const control of await controlHeights(signedOut)) {
      expect(control.height, `sign-in: ${control.name}`).toBeGreaterThanOrEqual(MIN_CONTROL_HEIGHT - 0.5);
    }
    await context.close();
  });
});
