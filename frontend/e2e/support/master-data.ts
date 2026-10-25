import { expect, Page } from '@playwright/test';
import { academicYearAround, signInAsTestAdmin } from './test-data';

const SEEDED_CATEGORY_CODES = ['O/S', 'CEW', 'W', 'STAFF', 'S STAFF', 'RTE', 'CASUAL'];

async function xsrfToken(page: Page): Promise<string> {
  const cookies = (await page.context().request.storageState()).cookies;
  const token = cookies.find((cookie) => cookie.name === 'XSRF-TOKEN')?.value;
  expect(token, 'sign in first: the session needs an XSRF-TOKEN cookie').toBeTruthy();
  return token as string;
}

/** Calls the API as the signed-in test admin (call signInAsTestAdmin first). Returns the status and parsed body. */
export async function callApi(
  page: Page,
  method: 'GET' | 'POST' | 'PUT' | 'DELETE',
  url: string,
  data?: unknown,
): Promise<{ status: number; body: any }> {
  const response = await page.context().request.fetch(url, {
    method,
    data,
    headers: { 'X-XSRF-TOKEN': await xsrfToken(page) },
  });
  const text = await response.text();
  return { status: response.status(), body: text ? JSON.parse(text) : null };
}

async function allRows(page: Page, path: string): Promise<any[]> {
  return (await callApi(page, 'POST', `${path}/search`, { first: 0, rows: 100, filters: {} })).body.rows;
}

/**
 * Makes sure one academic year covers today and returns it. Without one the app blocks every screen but Academic
 * years (FR-013), so a spec that is not about that block needs it. Call after signInAsTestAdmin.
 */
export async function ensureCurrentYear(page: Page): Promise<{ id: number; name: string; version: number }> {
  const now = new Date();
  const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
  const years: { id: number; name: string; version: number; startDate: string; endDate: string }[] = (
    await callApi(page, 'GET', '/api/academic-years')
  ).body;
  const covering = years.find((year) => year.startDate <= today && today <= year.endDate);
  return covering ?? (await callApi(page, 'POST', '/api/academic-years', academicYearAround())).body;
}

/** For a spec that starts signed out (auth.spec): makes sure a current year exists, then drops the session. */
export async function ensureCurrentYearThenSignOut(page: Page): Promise<void> {
  await signInAsTestAdmin(page);
  await ensureCurrentYear(page);
  await page.context().clearCookies();
}

/**
 * Empties the master data a spec creates (divisions, then academic years, then standards) and removes categories
 * that are not in the seed, so specs do not depend on each other. Call after signInAsTestAdmin. Unless told
 * otherwise it then adds the academic year around today, because with no current year the app blocks every screen
 * but Academic years (FR-013); pass `{ currentYear: false }` for a spec that wants none.
 */
export async function clearMasterData(page: Page, options: { currentYear?: boolean } = {}): Promise<void> {
  for (const row of await allRows(page, '/api/divisions')) {
    await callApi(page, 'DELETE', `/api/divisions/${row.id}`);
  }
  for (const row of await allRows(page, '/api/academic-years')) {
    await callApi(page, 'DELETE', `/api/academic-years/${row.id}`);
  }
  for (const row of await allRows(page, '/api/standards')) {
    await callApi(page, 'DELETE', `/api/standards/${row.id}`);
  }
  for (const row of await allRows(page, '/api/student-categories')) {
    if (!SEEDED_CATEGORY_CODES.includes(row.code)) {
      await callApi(page, 'DELETE', `/api/student-categories/${row.id}`);
    }
  }
  if (options.currentYear !== false) {
    await ensureCurrentYear(page);
  }
}
