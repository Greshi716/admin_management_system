import { expect, Page } from '@playwright/test';

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
 * Empties the master data a spec creates (divisions, then academic years, then standards) and removes categories
 * that are not in the seed, so specs do not depend on each other. Call after signInAsTestAdmin.
 */
export async function clearMasterData(page: Page): Promise<void> {
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
}
