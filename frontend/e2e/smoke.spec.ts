import { expect, test } from '@playwright/test';
import { academicYearAround } from './support/test-data';

// Checks the Playwright set-up itself: both servers start, the proxy reaches the API, the helpers behave.
test.describe('e2e set-up', () => {
  test('the API is reachable through the UI proxy and refuses an unknown visitor', async ({ request }) => {
    const response = await request.get('/api/auth/me');

    expect(response.status()).toBe(401);
  });

  test('the Angular app is served', async ({ page }) => {
    await page.goto('/');

    await expect(page.locator('app-root')).toBeAttached();
  });

  test('academicYearAround gives the April to March year that holds the date', () => {
    expect(academicYearAround(new Date(2026, 9, 25))).toEqual({
      name: '2026-27',
      startDate: '2026-04-01',
      endDate: '2027-03-31',
    });
    expect(academicYearAround(new Date(2027, 1, 10))).toMatchObject({ name: '2026-27' });
    expect(academicYearAround(new Date(2027, 3, 1))).toMatchObject({ name: '2027-28' });
    expect(academicYearAround(new Date(2026, 9, 25), -1)).toMatchObject({ name: '2025-26', endDate: '2026-03-31' });
  });
});
