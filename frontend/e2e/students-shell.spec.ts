import { expect, Page, test } from '@playwright/test';
import { clearMasterData } from './support/master-data';
import { createStudent, createStudentMasterData, studentInput } from './support/students';
import { signInAsTestAdmin } from './support/test-data';

/** Relative luminance of "rgb(r, g, b)" as computed by the browser, and the WCAG contrast of two such colours. */
function luminance(rgb: string): number {
  const [r, g, b] = (rgb.match(/[\d.]+/g) ?? ['0', '0', '0']).slice(0, 3).map((value) => {
    const channel = Number(value) / 255;
    return channel <= 0.03928 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrast(a: string, b: string): number {
  const [light, dark] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (light + 0.05) / (dark + 0.05);
}

async function colours(page: Page, selector: string): Promise<{ text: string; background: string }> {
  return page.locator(selector).first().evaluate((element) => {
    const style = getComputedStyle(element);
    return { text: style.color, background: style.backgroundColor };
  });
}

test.describe('the shell (F1 US4)', () => {
  test.beforeEach(async ({ page }) => {
    await signInAsTestAdmin(page);
    await clearMasterData(page);
  });

  test('FR-014: the top bar shows exactly the tabs Students, Finance and Setup and no unbuilt module', async ({ page }) => {
    await page.goto('/');

    const tabs = page.getByRole('navigation', { name: 'Main' }).getByRole('link');
    await expect(tabs).toHaveText(['Students', 'Finance', 'Setup']);
    for (const unbuilt of ['Dashboard', 'Approvals', 'Staff', 'Reports']) {
      await expect(page.getByRole('navigation', { name: 'Main' }).getByText(unbuilt)).toHaveCount(0);
    }
    await expect(page.getByTestId('current-year')).toBeVisible();
    await expect(page.getByTestId('user-name')).toHaveText(/\S/);
    await expect(page.getByRole('button', { name: 'Change password' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Log out' })).toBeVisible();
  });

  test('FR-014: Setup shows its left list next to the content', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'Setup' }).click();

    await expect(page).toHaveURL(/\/setup\/academic-years$/);
    const list = page.getByRole('navigation', { name: 'Setup' });
    await expect(list.getByRole('link')).toHaveText([
      'Academic years',
      'Standards',
      'Divisions',
      'Student categories',
      'Fee structure',
      'Audit log',
    ]);
    await expect(list.getByText('Master data')).toBeVisible();
    await expect(list.getByText('System')).toBeVisible();
    const listBox = (await list.boundingBox())!;
    const contentBox = (await page.getByRole('heading', { name: 'Academic years' }).boundingBox())!;
    expect(contentBox.x).toBeGreaterThan(listBox.x + listBox.width - 1); // beside, not under
    await expect(page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'Setup' })).toHaveClass(/bg-accent/);
  });

  test('FR-014: the old master data and audit log addresses still open the Setup screens', async ({ page }) => {
    await page.goto('/master-data/standards');
    await expect(page).toHaveURL(/\/setup\/standards$/);
    await page.goto('/audit-log');
    await expect(page).toHaveURL(/\/setup\/audit-log$/);
  });

  test('FR-014: the content is centred and at most 1280 px wide on a wide window', async ({ page }) => {
    await page.setViewportSize({ width: 1800, height: 900 });
    await page.goto('/students');
    await expect(page.getByRole('heading', { name: 'Students' })).toBeVisible();

    const box = (await page.locator('main').boundingBox())!;
    expect(box.width).toBeLessThanOrEqual(1280);
    expect(Math.abs(box.x - (1800 - box.x - box.width))).toBeLessThanOrEqual(2); // centred
  });

  test('FR-014: the bar text and the active tab have a contrast of at least 4.5 to 1', async ({ page }) => {
    await page.goto('/students');
    await expect(page.getByRole('heading', { name: 'Students' })).toBeVisible();

    const bar = await colours(page, 'header');
    const logoText = await page.locator('header a').first().evaluate((element) => getComputedStyle(element).color);
    expect(contrast(logoText, bar.background)).toBeGreaterThanOrEqual(4.5);
    const active = await colours(page, 'nav[aria-label="Main"] a.bg-accent');
    expect(active.background).not.toBe('rgba(0, 0, 0, 0)');
    expect(contrast(active.text, active.background)).toBeGreaterThanOrEqual(4.5);
    const idle = await colours(page, 'nav[aria-label="Main"] a:not(.bg-accent)');
    expect(contrast(idle.text, bar.background)).toBeGreaterThanOrEqual(4.5);
  });

  for (const viewport of [
    { name: 'tablet', width: 768, height: 1024 },
    { name: 'phone', width: 375, height: 700 },
  ]) {
    test(`FR-014, FR-015: at ${viewport.name} width the page does not scroll sideways and a wide table scrolls in its card`, async ({
      page,
    }) => {
      const master = await createStudentMasterData(page);
      await createStudent(page, studentInput(1, master));
      await page.setViewportSize({ width: viewport.width, height: viewport.height });
      await page.goto('/students');
      await expect(page.locator('tbody tr').first()).toBeVisible();

      const pageOverflow = await page.evaluate(
        () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
      );
      expect(pageOverflow).toBeLessThanOrEqual(0);
      await expect(page.getByRole('button', { name: 'Log out' })).toBeVisible();
      await expect(page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'Students' })).toBeVisible();
      if (viewport.width < 500) {
        const scrolls = await page
          .locator('table')
          .first()
          .evaluate((table) => {
            const wrapper = table.parentElement as HTMLElement;
            return wrapper.scrollWidth > wrapper.clientWidth && getComputedStyle(wrapper).overflowX !== 'visible';
          });
        expect(scrolls).toBe(true);
      }
    });
  }

  test('F1 FR-015 as changed by F3: the Students list and the profile have no promote, roll number or LC items (they now show fees, F3 FR-014, FR-015)', async ({ page }) => {
    const master = await createStudentMasterData(page);
    const student = await createStudent(page, studentInput(1, master));
    const absent = ['Prepare LC', 'Promote', 'Left students (LC)', 'Roll'];

    for (const path of ['/students', `/students/${student.id}`]) {
      await page.goto(path);
      await expect(page.getByRole('heading').first()).toBeVisible();
      const text = await page.locator('main').innerText();
      for (const word of absent) {
        expect(text, `${path} shows "${word}"`).not.toContain(word);
      }
    }
  });
});

test.describe('the shell without a current academic year (F1 US4)', () => {
  test.beforeEach(async ({ page }) => {
    await signInAsTestAdmin(page);
    await clearMasterData(page, { currentYear: false });
  });

  test('FR-013: the Students tab is closed and direct links to Students screens go to Academic years', async ({ page }) => {
    await page.goto('/setup/academic-years');
    await expect(page.getByTestId('no-current-year')).toBeVisible();
    const bar = page.getByRole('navigation', { name: 'Main' });
    await expect(bar.getByRole('link', { name: 'Students' })).toHaveCount(0);
    await expect(bar.getByText('Students', { exact: true })).toHaveAttribute('aria-disabled', 'true');
    await expect(bar.getByText('Finance', { exact: true })).toHaveAttribute('aria-disabled', 'true');
    await expect(bar.getByRole('link', { name: 'Setup' })).toBeVisible();

    for (const path of ['/students', '/students/new', '/students/1']) {
      await page.goto(path);
      await expect(page).toHaveURL(/\/setup\/academic-years$/);
      await expect(page.getByTestId('no-current-year')).toBeVisible();
    }
  });
});
