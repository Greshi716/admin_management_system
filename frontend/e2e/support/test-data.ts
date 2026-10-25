import { expect, Page } from '@playwright/test';

/**
 * Known sign-in for UI tests. Test-only values, not a real credential. The backend `test` profile sets the same
 * pair (campusflow.test-admin.* in application-test.properties) and the first-run bootstrap creates this admin
 * with the password already marked as changed.
 */
export const TEST_ADMIN = {
  username: 'testadmin',
  password: 'Test-Admin-Password-1',
} as const;

/**
 * Signs in through the API so a spec starts on a signed-in session without clicking through the form (the form
 * itself is covered by auth.spec.ts). The first call to the API answers 401 but sets the XSRF-TOKEN cookie, which
 * the sign-in POST must send back as a header.
 */
export async function signInAsTestAdmin(page: Page): Promise<void> {
  const api = page.context().request;
  await api.get('/api/auth/me');
  const cookies = (await api.storageState()).cookies;
  const token = cookies.find((cookie) => cookie.name === 'XSRF-TOKEN')?.value;
  expect(token, 'the backend must set an XSRF-TOKEN cookie on the first response').toBeTruthy();

  const response = await api.post('/api/auth/login', {
    data: TEST_ADMIN,
    headers: { 'X-XSRF-TOKEN': token as string },
  });
  expect(response.ok(), `sign-in as the test admin failed with HTTP ${response.status()}`).toBeTruthy();
}

/**
 * Puts the test admin back in a known state: the known password, no lock-out, and the "password changed" flag as
 * asked. Uses the test-profile endpoint `/api/test-support/reset-admin`, which a real installation does not have.
 */
export async function resetTestAdmin(page: Page, options: { passwordChanged: boolean } = { passwordChanged: true }): Promise<void> {
  const api = page.context().request;
  await api.get('/api/auth/me');
  const cookies = (await api.storageState()).cookies;
  const token = cookies.find((cookie) => cookie.name === 'XSRF-TOKEN')?.value;
  expect(token, 'the backend must set an XSRF-TOKEN cookie on the first response').toBeTruthy();

  const response = await api.post(`/api/test-support/reset-admin?passwordChanged=${options.passwordChanged}`, {
    headers: { 'X-XSRF-TOKEN': token as string },
  });
  expect(response.ok(), `resetting the test admin failed with HTTP ${response.status()}`).toBeTruthy();
}

export interface YearDates {
  name: string;
  /** ISO date, yyyy-MM-dd. */
  startDate: string;
  endDate: string;
}

/**
 * The April-to-March school year that contains `today`, so specs never depend on a fixed calendar year. Pass
 * `offset` to get the year before (-1) or after (+1). The spec's examples use 01-06 to 31-05; the school's real dates
 * are set by the admin and no rule depends on them, so the specs use April to March as one fixed choice.
 */
export function academicYearAround(today: Date = new Date(), offset = 0): YearDates {
  const startYear = (today.getMonth() >= 3 ? today.getFullYear() : today.getFullYear() - 1) + offset;
  const endYear = startYear + 1;
  return {
    name: `${startYear}-${String(endYear).slice(-2)}`,
    startDate: `${startYear}-04-01`,
    endDate: `${endYear}-03-31`,
  };
}
