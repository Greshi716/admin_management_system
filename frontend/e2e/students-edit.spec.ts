import { expect, Page, test } from '@playwright/test';
import { callApi, clearMasterData } from './support/master-data';
import { createStudent, createStudentMasterData, studentInput, StudentMasterData } from './support/students';
import { signInAsTestAdmin } from './support/test-data';

// Synthetic values only (constitution VI).
const AADHAAR = '123456789012';

interface Seed {
  master: StudentMasterData;
  otherStandardId: number;
  otherDivisionId: number;
  otherCategoryId: number;
  otherCategoryName: string;
  studentId: number;
}

async function seed(page: Page): Promise<Seed> {
  const master = await createStudentMasterData(page);
  const otherStandard = (await callApi(page, 'POST', '/api/standards', { name: 'Test Standard Other', displayOrder: 2 })).body;
  const otherDivision = (
    await callApi(page, 'POST', '/api/divisions', { academicYearId: master.yearId, standardId: otherStandard.id, name: 'B' })
  ).body;
  const categories = (await callApi(page, 'POST', '/api/student-categories/search', { first: 0, rows: 100, filters: {} }))
    .body.rows;
  const otherCategory = categories.find((category: any) => category.id !== master.categoryId);
  const input: any = { ...studentInput(1, master), aadhaar: AADHAAR, religionCaste: 'Test Religion' };
  const student = await createStudent(page, input);
  return {
    master,
    otherStandardId: otherStandard.id,
    otherDivisionId: otherDivision.id,
    otherCategoryId: otherCategory.id,
    otherCategoryName: otherCategory.name,
    studentId: student.id,
  };
}

async function openEdit(page: Page, id: number): Promise<void> {
  await page.goto(`/students/${id}/edit`);
  await expect(page.getByRole('heading', { name: 'Edit student' })).toBeVisible();
  await expect(page.getByLabel('GR No. *')).toHaveValue(/E2E-/);
}

const save = (page: Page) => page.getByRole('button', { name: 'Save', exact: true });

test.describe('edit a student', () => {
  let data: Seed;

  test.beforeEach(async ({ page }) => {
    await signInAsTestAdmin(page);
    await clearMasterData(page);
    data = await seed(page);
  });

  test('FR-004: the admin edits the details and the profile shows them', async ({ page }) => {
    await openEdit(page, data.studentId);
    await expect(page.getByLabel('Aadhaar')).toHaveValue(AADHAAR); // the full value, only on this screen
    await expect(page.locator('#categoryId')).toHaveCount(0);

    await page.getByLabel('Address *').fill('New test address, Test Town');
    await save(page).click();

    await expect(page).toHaveURL(new RegExp(`/students/${data.studentId}$`));
    await expect(page.getByText('New test address, Test Town').first()).toBeVisible();
    await expect(page.getByTestId('aadhaar')).toHaveText('********9012');
  });

  test('FR-002, FR-004: a GR No. correction works and the old number can never be registered again', async ({ page }) => {
    await openEdit(page, data.studentId);
    await page.getByLabel('GR No. *').fill('E2E-CORRECTED');
    await save(page).click();
    await expect(page.getByText('GR No. E2E-CORRECTED')).toBeVisible();

    await page.goto('/students/new');
    await page.getByLabel('GR No. *').fill('e2e-1');
    await page.getByLabel('Surname *').fill('Anothersurname');
    await page.getByLabel('First name *').fill('Bravo');
    await page.getByLabel('Gender *').selectOption('Male');
    await page.getByLabel('Date of birth *').fill('2012-05-10');
    await page.getByLabel('Mobile 1 *').fill('9000000555');
    await page.getByLabel('Address *').fill('Test address');
    await page.getByLabel('Category *').selectOption({ index: 1 });
    await page.getByLabel('Admission date *').fill('2020-06-15');
    await page.getByLabel('Admission standard *').selectOption({ index: 1 });
    await page.getByLabel('Standard (this year) *').selectOption({ index: 1 });
    await page.getByLabel('Division (this year) *').selectOption({ index: 1 });
    await save(page).click();

    await expect(page.getByRole('alert').filter({ hasText: 'This GR No. is already used by a student.' })).toBeVisible();
  });

  test('FR-006: the class record is changed from the class history', async ({ page }) => {
    await page.goto(`/students/${data.studentId}`);
    await page.getByRole('button', { name: /^Change class record/ }).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByLabel('Standard *').selectOption({ label: 'Test Standard Other' });
    await dialog.getByLabel('Division *').selectOption({ label: 'B' });
    await dialog.getByLabel('Category *').selectOption({ label: data.otherCategoryName });
    await dialog.getByRole('button', { name: 'Save' }).click();

    await expect(page.getByTestId('class-history')).toContainText('Test Standard Other-B');
    await expect(page.getByTestId('heading-class')).toContainText('Test Standard Other-B');
    await expect(page.getByTestId('class-history').locator('tbody tr')).toHaveCount(1);
  });

  test('FR-018: two windows editing the same student: the second save is refused and the latest is shown', async ({ page, context }) => {
    const second = await context.newPage();
    await openEdit(page, data.studentId);
    await openEdit(second, data.studentId);

    await page.getByLabel('Address *').fill('First window address');
    await save(page).click();
    await expect(page).toHaveURL(new RegExp(`/students/${data.studentId}$`));

    await second.getByLabel('Address *').fill('Second window address');
    await save(second).click();

    await expect(second.getByRole('heading', { name: 'This record was changed' })).toBeVisible();
    await expect(second.getByTestId('latest-values')).toContainText('First window address');
    await second.getByRole('button', { name: 'Use the latest values' }).click();
    await expect(second.getByLabel('Address *')).toHaveValue('First window address');
    // nothing was overwritten
    const saved = await callApi(page, 'GET', `/api/students/${data.studentId}`);
    expect(saved.body.address).toBe('First window address');
  });

  test('FR-018: two windows changing the same class record: the second save is refused and the latest is shown', async ({ page, context }) => {
    const second = await context.newPage();
    for (const p of [page, second]) {
      await p.goto(`/students/${data.studentId}`);
      await p.getByRole('button', { name: /^Change class record/ }).click();
      await expect(p.getByRole('dialog').getByLabel('Standard *')).toBeVisible();
    }

    await page.getByRole('dialog').getByLabel('Standard *').selectOption({ label: 'Test Standard Other' });
    await page.getByRole('dialog').getByLabel('Division *').selectOption({ label: 'B' });
    await page.getByRole('dialog').getByRole('button', { name: 'Save' }).click();
    await expect(page.getByTestId('class-history')).toContainText('Test Standard Other-B');

    await second.getByRole('dialog').getByLabel('Category *').selectOption({ label: data.otherCategoryName });
    await second.getByRole('dialog').getByRole('button', { name: 'Save' }).click();

    await expect(second.getByRole('heading', { name: 'This record was changed' })).toBeVisible();
    await expect(second.getByTestId('latest-values')).toContainText('Test Standard Other-B');
    const record = (await callApi(page, 'GET', `/api/students/${data.studentId}`)).body.classHistory[0];
    expect(record.categoryId).toBe(data.master.categoryId); // the second save did not overwrite anything
  });

  test('FR-004, FR-009: Mark as left asks first, hides the student from the default list and cannot be undone', async ({ page }) => {
    await page.goto(`/students/${data.studentId}`);

    await page.getByRole('button', { name: 'Mark as left' }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toContainText('cannot be undone');
    await dialog.getByRole('button', { name: 'Cancel' }).click();
    await expect(page.getByTestId('status-badge')).toHaveText('Active');

    await page.getByRole('button', { name: 'Mark as left' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Mark as left' }).click();
    await expect(page.getByTestId('status-badge')).toHaveText('Left');
    await expect(page.getByRole('button', { name: 'Mark as left' })).toHaveCount(0);

    // no way back, even by calling the API
    const again = await callApi(page, 'PUT', `/api/students/${data.studentId}/left`, { version: 0 });
    expect([409, 422]).toContain(again.status);

    await page.goto('/students');
    await expect(page.getByText('No rows found')).toBeVisible();
    await page.getByLabel('Status', { exact: true }).selectOption('Left');
    await expect(page.locator('tbody tr')).toHaveCount(1);
    await page.getByLabel('Status', { exact: true }).selectOption('All');
    await expect(page.locator('tbody tr')).toHaveCount(1);
  });

  test('FR-002, FR-004: a student who left is registered again with a new GR No. and the old one stays reserved', async ({ page }) => {
    await callApi(page, 'PUT', `/api/students/${data.studentId}/left`, { version: 0 });

    const again = await createStudent(page, { ...studentInput(1, data.master), grNo: 'E2E-AGAIN' });
    expect(again.grNo).toBe('E2E-AGAIN');
    const reused = await callApi(page, 'POST', '/api/students', { ...studentInput(1, data.master), grNo: 'E2E-1' });
    expect(reused.status).toBe(409);
  });

  test('FR-004, FR-006, FR-018: the History block lists every change', async ({ page }) => {
    await openEdit(page, data.studentId);
    await page.getByLabel('Address *').fill('History test address');
    await save(page).click();
    await expect(page).toHaveURL(new RegExp(`/students/${data.studentId}$`));

    await page.getByRole('button', { name: /^Change class record/ }).click();
    await page.getByRole('dialog').getByLabel('Standard *').selectOption({ label: 'Test Standard Other' });
    await page.getByRole('dialog').getByLabel('Division *').selectOption({ label: 'B' });
    await page.getByRole('dialog').getByRole('button', { name: 'Save' }).click();
    await expect(page.getByTestId('class-history')).toContainText('Test Standard Other-B');

    await page.getByRole('button', { name: 'Mark as left' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Mark as left' }).click();
    await expect(page.getByTestId('status-badge')).toHaveText('Left');

    const history = page.getByTestId('history');
    await expect(history).toContainText('History test address');
    await expect(history).toContainText('Standard');
    await expect(history).toContainText('Status: Active → Left');
    await expect(history).not.toContainText(AADHAAR);
  });
});
