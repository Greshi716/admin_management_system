import { expect, Locator, Page, test } from '@playwright/test';
import { callApi, clearMasterData } from './support/master-data';
import { createStudent, createStudentMasterData, studentInput, StudentMasterData } from './support/students';
import { academicYearAround, signInAsTestAdmin } from './support/test-data';
import { readFirstSheet } from './support/xlsx';

// Synthetic values only (constitution VI).
const AADHAAR = '123456789012';
const MOBILE_2 = '9000000777';
const STUDENT_COUNT = 12;

interface Seed {
  master: StudentMasterData;
  /** A second standard with a division this year, and a second category: the filters need something to tell apart. */
  otherStandardId: number;
  otherDivisionId: number;
  otherCategoryId: number;
  /** id by number n (1 to STUDENT_COUNT) */
  ids: Map<number, number>;
}

/**
 * 12 students E2E-01 to E2E-12. Student 1 has Mobile 2, Aadhaar and religion/caste; student 2 is in the other
 * standard and category; the rest share the first standard and category.
 */
async function seedStudents(page: Page): Promise<Seed> {
  const master = await createStudentMasterData(page);
  const otherStandard = (await callApi(page, 'POST', '/api/standards', { name: 'Test Standard Other', displayOrder: 2 })).body;
  const otherDivision = (
    await callApi(page, 'POST', '/api/divisions', { academicYearId: master.yearId, standardId: otherStandard.id, name: 'B' })
  ).body;
  const categories = (await callApi(page, 'POST', '/api/student-categories/search', { first: 0, rows: 100, filters: {} }))
    .body.rows;
  const otherCategoryId = categories.find((category: any) => category.id !== master.categoryId).id;

  const ids = new Map<number, number>();
  for (let n = 1; n <= STUDENT_COUNT; n++) {
    const input: any = { ...studentInput(n, master), grNo: `E2E-${String(n).padStart(2, '0')}` };
    if (n === 1) {
      input.mobile2 = MOBILE_2;
      input.aadhaar = AADHAAR;
      input.religionCaste = 'Test Religion';
    }
    if (n === 2) {
      input.standardId = otherStandard.id;
      input.divisionId = otherDivision.id;
      input.categoryId = otherCategoryId;
    }
    ids.set(n, (await createStudent(page, input)).id);
  }
  return { master, otherStandardId: otherStandard.id, otherDivisionId: otherDivision.id, otherCategoryId, ids };
}

async function openList(page: Page): Promise<void> {
  await page.goto('/students');
  await expect(page.getByRole('heading', { name: 'Students' })).toBeVisible();
  await expect(page.locator('tbody tr').first()).toBeVisible();
}

function search(page: Page): Locator {
  return page.getByRole('searchbox', { name: 'Search this list' });
}

async function grNumbers(page: Page): Promise<string[]> {
  const rows = page.locator('tbody tr');
  const count = await rows.count();
  const found: string[] = [];
  for (let i = 0; i < count; i++) {
    found.push((await rows.nth(i).locator('td').nth(1).innerText()).trim());
  }
  return found;
}

test.describe('students list and profile', () => {
  let seed: Seed;

  test.beforeEach(async ({ page }) => {
    await signInAsTestAdmin(page);
    await clearMasterData(page);
    seed = await seedStudents(page);
  });

  test('FR-008: search finds a student by name, by GR No. and by Mobile 2', async ({ page }) => {
    await openList(page);

    await search(page).fill('testsurname5');
    await expect(page.getByText('of 1 rows')).toBeVisible();
    await expect.poll(() => grNumbers(page)).toEqual(['E2E-05']);

    await search(page).fill('e2e-09');
    await expect(page.getByText('of 1 rows')).toBeVisible();
    await expect.poll(() => grNumbers(page)).toEqual(['E2E-09']);

    await search(page).fill(MOBILE_2);
    await expect(page.getByText('of 1 rows')).toBeVisible();
    await expect.poll(() => grNumbers(page)).toEqual(['E2E-01']);
  });

  test('FR-008: filters on Standard and Category and the sort apply to the whole list', async ({ page }) => {
    await openList(page);

    await page.getByRole('button', { name: 'Filter Std / Div' }).click();
    await page
      .getByRole('group', { name: 'Filter Std / Div' })
      .locator('select').first()
      .selectOption({ label: 'Test Standard Other' });
    await expect(page.getByText('of 1 rows')).toBeVisible();
    await expect.poll(() => grNumbers(page)).toEqual(['E2E-02']);

    await page.getByRole('group', { name: 'Filter Std / Div' }).locator('select').first().selectOption({ label: 'All' });
    await expect(page.getByText(`of ${STUDENT_COUNT} rows`)).toBeVisible();
    await page.getByRole('button', { name: 'Filter Category' }).click();
    await page
      .getByRole('group', { name: 'Filter Category' })
      .locator('select').first()
      .selectOption({ value: String(seed.otherCategoryId) });
    await expect(page.getByText('of 1 rows')).toBeVisible();
    await expect.poll(() => grNumbers(page)).toEqual(['E2E-02']);
  });

  test('FR-008: sorting by GR No. orders the whole list and the page size is 10', async ({ page }) => {
    await openList(page);
    await page.getByLabel('Rows per page').selectOption('10');
    await expect(page.getByText(`Showing 1 to 10 of ${STUDENT_COUNT} rows`)).toBeVisible();
    await expect(page.locator('tbody tr')).toHaveCount(10);

    const heading = page.getByRole('button', { name: /^GR No\./ });
    await heading.click();
    await heading.click();
    await expect(page.locator('tbody tr').first()).toContainText('E2E-12');
    expect((await grNumbers(page))[0]).toBe('E2E-12');

    await page.getByRole('button', { name: 'Next page' }).click();
    await expect(page.getByText(`Showing 11 to ${STUDENT_COUNT} of ${STUDENT_COUNT} rows`)).toBeVisible();
    await expect.poll(() => grNumbers(page)).toEqual(['E2E-02', 'E2E-01']);
  });

  test('FR-008, FR-010: the export has every matching row and the sensitive columns masked', async ({ page }) => {
    await openList(page);

    const [download] = await Promise.all([page.waitForEvent('download'), page.getByTestId('export').click()]);

    expect(download.suggestedFilename()).toMatch(/^students-\d{4}-\d{2}-\d{2}\.xlsx$/);
    const sheet = readFirstSheet(await download.path());
    const headers = sheet[0];
    expect(headers).toContain('GR No.');
    expect(headers).toContain('Aadhaar');
    expect(headers).toContain('Religion / caste');
    const rows = sheet.slice(1);
    expect(rows).toHaveLength(STUDENT_COUNT); // not only the 10 shown on the page
    const first = rows.find((row) => row[headers.indexOf('GR No.')] === 'E2E-01') as string[];
    expect(first[headers.indexOf('Aadhaar')]).toBe('********9012');
    expect(first[headers.indexOf('Religion / caste')]).not.toContain('Test Religion');
    expect(rows.flat().join('|')).not.toContain(AADHAAR);
  });

  test('FR-010: searching by Aadhaar finds nothing and a direct filter on it is refused', async ({ page }) => {
    await openList(page);

    await search(page).fill(AADHAAR);
    await expect(page.getByText('No rows found')).toBeVisible();

    const refused = await callApi(page, 'POST', '/api/students/search', {
      first: 0,
      rows: 10,
      filters: { aadhaar: { value: AADHAAR, matchMode: 'equals' } },
    });
    expect(refused.status).toBe(422);
    expect(JSON.stringify(refused.body)).not.toContain(AADHAAR);
  });

  test('FR-008: a student with only an earlier-year record shows that record with its year', async ({ page }) => {
    const earlier = academicYearAround(new Date(), -1);
    const earlierYear = (await callApi(page, 'POST', '/api/academic-years', earlier)).body;
    const earlierDivision = (
      await callApi(page, 'POST', '/api/divisions', {
        academicYearId: earlierYear.id,
        standardId: seed.master.standardId,
        name: 'Z',
      })
    ).body;
    const moved = await page.context().request.fetch(
      `/api/test-support/students/${seed.ids.get(3)}/move-record?academicYearId=${earlierYear.id}` +
        `&divisionId=${earlierDivision.id}&yearStartDate=${earlier.startDate}`,
      { method: 'POST', headers: { 'X-XSRF-TOKEN': await xsrf(page) } },
    );
    expect(moved.status()).toBe(204);

    await openList(page);
    await search(page).fill('E2E-03');
    await expect(page.getByText('of 1 rows')).toBeVisible();

    const row = page.locator('tbody tr').first();
    await expect(row).toContainText('-Z');
    await expect(row.getByTestId('record-year')).toHaveText(earlier.name);

    await search(page).fill('E2E-04');
    await expect(page.getByText('of 1 rows')).toBeVisible();
    await expect(page.locator('tbody tr').first().getByTestId('record-year')).toHaveCount(0);
  });

  test('FR-007, FR-015: View profile opens one page with the details and none of the fee items', async ({ page }) => {
    await openList(page);
    await search(page).fill('E2E-01');
    await expect(page.getByText('of 1 rows')).toBeVisible();

    await page.getByRole('link', { name: 'View profile' }).click();

    await expect(page).toHaveURL(new RegExp(`/students/${seed.ids.get(1)}$`));
    await expect(page.getByTestId('student-name')).toHaveText('Alpha Middle Testsurname1');
    await expect(page.getByTestId('status-badge')).toHaveText('Active');
    await expect(page.getByText('GR No. E2E-01')).toBeVisible();
    await expect(page.getByTestId('heading-class')).toContainText('Test Standard');
    await expect(page.getByRole('heading', { name: 'Personal details' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Contact and admission' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Class history' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'History', exact: true })).toBeVisible();
    // Mobiles in full, Aadhaar and religion/caste masked.
    await expect(page.getByText('9000000001').first()).toBeVisible();
    await expect(page.getByText(MOBILE_2, { exact: true })).toBeVisible();
    await expect(page.getByTestId('aadhaar')).toHaveText('********9012');
    await expect(page.getByTestId('religion-caste')).not.toContainText('Test Religion');
    await expect(page.getByTestId('class-history').locator('tbody tr')).toHaveCount(1);
    await expect(page.getByRole('link', { name: 'Edit details' })).toBeVisible();

    const content = page.locator('main');
    for (const absent of ['Collect fee', 'Receipt', 'Roll', 'leaving certificate', 'Fee']) {
      await expect(content).not.toContainText(absent);
    }
    await expect(content).not.toContainText(AADHAAR);

    await page.getByRole('link', { name: '← Back to students' }).click();
    await expect(page).toHaveURL(/\/students$/);
  });

  test('FR-009, FR-019: the History block shows the registration and then an edit with old and new values', async ({ page }) => {
    const id = seed.ids.get(5);
    await page.goto(`/students/${id}`);
    await expect(page.getByTestId('history').locator('li').first()).toContainText('Create');
    await expect(page.getByTestId('history')).not.toContainText('Edit');

    const edited = await page.context().request.fetch(
      `/api/test-support/students/${id}/edit-address?address=${encodeURIComponent('New test address, Test Town')}`,
      { method: 'POST', headers: { 'X-XSRF-TOKEN': await xsrf(page) } },
    );
    expect(edited.status()).toBe(204);
    await page.reload();

    const first = page.getByTestId('history').locator('li').first();
    await expect(first).toContainText('Edit');
    await expect(first).toContainText('Address');
    await expect(first).toContainText('Test address 5, Test Town');
    await expect(first).toContainText('New test address, Test Town');
  });
});

async function xsrf(page: Page): Promise<string> {
  const cookies = (await page.context().request.storageState()).cookies;
  return cookies.find((cookie) => cookie.name === 'XSRF-TOKEN')?.value as string;
}
