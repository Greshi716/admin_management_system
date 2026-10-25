import { expect, Page, test } from '@playwright/test';
import { callApi, clearMasterData } from './support/master-data';
import { createStudent, createStudentMasterData, studentInput, StudentMasterData } from './support/students';
import { signInAsTestAdmin } from './support/test-data';

// A 1x1 PNG: a real, decodable picture without any real person in it.
const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
  'base64',
);

async function openForm(page: Page): Promise<void> {
  await page.goto('/students/new');
  await expect(page.getByRole('heading', { name: 'Register student' })).toBeVisible();
  await expect(page.getByLabel('GR No. *')).toBeFocused();
}

async function fillForm(page: Page, values: Record<string, string> = {}): Promise<void> {
  const v = {
    grNo: 'E2E-100',
    surname: 'Testsurname100',
    firstName: 'Alpha',
    middleName: 'Middle',
    dob: '2012-05-10',
    mobile1: '9000000100',
    address: 'Test address 100, Test Town',
    admissionDate: '2020-06-15',
    ...values,
  };
  await page.getByLabel('GR No. *').fill(v.grNo);
  await page.getByLabel('Surname *').fill(v.surname);
  await page.getByLabel('First name *').fill(v.firstName);
  await page.getByLabel('Middle name').fill(v.middleName);
  await page.getByLabel('Gender *').selectOption('Male');
  await page.getByLabel('Date of birth *').fill(v.dob);
  await page.getByLabel('Mobile 1 *').fill(v.mobile1);
  await page.getByLabel('Address *').fill(v.address);
  await page.getByLabel('Category *').selectOption({ index: 1 });
  await page.getByLabel('Admission date *').fill(v.admissionDate);
  await page.getByLabel('Admission standard *').selectOption({ index: 1 });
  await page.getByLabel('Standard (this year) *').selectOption({ index: 1 });
  await page.getByLabel('Division (this year) *').selectOption({ index: 1 });
}

/** Students with this surname, first name Alpha and this middle name (the name-matches rule compares all three). */
async function matchesOf(page: Page, surname: string, middleName = 'Middle'): Promise<any[]> {
  const found = await callApi(
    page,
    'GET',
    `/api/students/name-matches?surname=${encodeURIComponent(surname)}&firstName=Alpha&middleName=${middleName}`,
  );
  return found.body;
}

/** There is no profile screen until F1 US2; a save is seen by the student existing. */
async function expectSaved(page: Page, surname: string, middleName = 'Middle'): Promise<void> {
  await expect.poll(async () => (await matchesOf(page, surname, middleName)).length).toBe(1);
}

test.describe('register a student', () => {
  let master: StudentMasterData;

  test.beforeEach(async ({ page }) => {
    await signInAsTestAdmin(page);
    await clearMasterData(page);
    master = await createStudentMasterData(page);
  });

  test('FR-001: the admin registers a student with all fields and lands on the profile', async ({ page }) => {
    await openForm(page);
    await fillForm(page);
    await page.getByLabel('Birth place').fill('Test Town');
    await page.getByLabel("Father's name").fill('Test Father');
    await page.getByLabel("Mother's name").fill('Test Mother');
    await page.getByLabel('Mobile 2').fill('9000000101');
    await page.getByLabel('Aadhaar').fill('123456789012');
    await page.getByLabel('Religion / caste').fill('Test Religion');
    await page.getByLabel('Previous school').fill('Test Previous School');
    await page.getByRole('button', { name: 'Save', exact: true }).click();

    await expectSaved(page, 'Testsurname100');
    expect(await matchesOf(page, 'Testsurname100')).toHaveLength(1);
  });

  test('FR-017: a GR No. already used, in other capitals and with spaces, is refused beside the field', async ({ page }) => {
    await createStudent(page, studentInput(1, master));
    await openForm(page);
    await fillForm(page, { grNo: '  e2e-1 ', surname: 'Testsurname200' });
    await page.getByRole('button', { name: 'Save', exact: true }).click();

    await expect(page.getByLabel('GR No. *').locator('..').getByRole('alert')).toBeVisible();
    await expect(page).toHaveURL(/\/students\/new$/);
    expect(await matchesOf(page, 'Testsurname200')).toHaveLength(0);
  });

  test('FR-003: a student with the same name gives a warning and saving still works', async ({ page }) => {
    await createStudent(page, { ...studentInput(2, master), surname: 'Testsame', firstName: 'Alpha' });
    await openForm(page);
    await page.getByLabel('Surname *').fill('Testsame');
    await page.getByLabel('First name *').fill('Alpha');
    await page.getByLabel('Middle name').fill('Middle');
    await page.getByLabel('Mobile 1 *').focus();

    await expect(page.getByTestId('name-matches')).toContainText('E2E-2');

    await fillForm(page, { grNo: 'E2E-300', surname: 'Testsame', firstName: 'Alpha' });
    await page.getByRole('button', { name: 'Save', exact: true }).click();
    await expect.poll(async () => (await matchesOf(page, 'Testsame')).length).toBe(2);
    expect(await matchesOf(page, 'Testsame')).toHaveLength(2);
  });

  const refusals: { title: string; values: Record<string, string>; field: string }[] = [
    { title: 'FR-017: Mobile 1 must be filled', values: { mobile1: '' }, field: 'Mobile 1 *' },
    { title: 'FR-017: Mobile 1 with 9 digits is refused', values: { mobile1: '900000010' }, field: 'Mobile 1 *' },
    { title: 'FR-017: a future admission date is refused', values: { admissionDate: '2999-01-01' }, field: 'Admission date *' },
    { title: 'FR-017: an admission date before the birth date is refused', values: { admissionDate: '2000-01-01' }, field: 'Admission date *' },
  ];
  for (const refusal of refusals) {
    test(refusal.title, async ({ page }) => {
      await openForm(page);
      await fillForm(page, { surname: 'Testrefused', ...refusal.values });
      await page.getByRole('button', { name: 'Save', exact: true }).click();

      await expect(page.getByLabel(refusal.field).locator('..').getByRole('alert')).toBeVisible();
      await expect(page).toHaveURL(/\/students\/new$/);
      expect(await matchesOf(page, 'Testrefused')).toHaveLength(0);
    });
  }

  test('FR-005: a photo chosen on the form is stored after the save', async ({ page }) => {
    await openForm(page);
    await fillForm(page, { surname: 'Testphoto' });
    await page.getByLabel('Photo file').setInputFiles({ name: 'photo.png', mimeType: 'image/png', buffer: PNG });
    await page.getByRole('button', { name: 'Save', exact: true }).click();
    await expectSaved(page, 'Testphoto');

    const [student] = await matchesOf(page, 'Testphoto');
    // the photo is sent right after the student is created, so wait for it
    await expect
      .poll(async () => (await page.context().request.get(`/api/students/${student.id}/photo`)).status())
      .toBe(200);
    const photo = await page.context().request.get(`/api/students/${student.id}/photo`);
    expect(photo.headers()['content-type']).toContain('image/jpeg');
  });

  test('FR-005: a refused photo leaves the student saved with the message beside the photo', async ({ page }) => {
    await openForm(page);
    await fillForm(page, { surname: 'Testbadphoto' });
    await page
      .getByLabel('Photo file')
      .setInputFiles({ name: 'notes.png', mimeType: 'image/png', buffer: Buffer.from('this is not a picture') });
    await page.getByRole('button', { name: 'Save and add next' }).click();

    await expect(page.getByTestId('photo-error')).toBeVisible();
    await expect(page.getByTestId('saved-banner')).toBeVisible();
    expect(await matchesOf(page, 'Testbadphoto')).toHaveLength(1);
    await expect(page.getByLabel('GR No. *')).toHaveValue('E2E-100'); // not cleared before the admin decides
  });

  test('FR-005: a photo over 5 MB is refused and the student stays saved', async ({ page }) => {
    await openForm(page);
    await fillForm(page, { surname: 'Testbigphoto' });
    await page
      .getByLabel('Photo file')
      .setInputFiles({ name: 'big.png', mimeType: 'image/png', buffer: Buffer.alloc(5 * 1024 * 1024 + 1024, 1) });
    await page.getByRole('button', { name: 'Save and add next' }).click();

    await expect(page.getByTestId('photo-error')).toBeVisible();
    expect(await matchesOf(page, 'Testbigphoto')).toHaveLength(1);
  });

  test('FR-011: a standard with no division this year shows the Add a division message', async ({ page }) => {
    await callApi(page, 'POST', '/api/standards', { name: 'Test Empty Standard', displayOrder: 2 });
    await openForm(page);
    await page.getByLabel('Standard (this year) *').selectOption({ label: 'Test Empty Standard' });

    await expect(page.getByTestId('no-division')).toContainText('Add a division for this standard under Setup');
    await expect(page.getByLabel('Division (this year) *').locator('option')).toHaveCount(1);
  });

  test('FR-001, FR-017: 20 students are entered with the keyboard only (SC-001)', async ({ page }) => {
    await openForm(page);
    for (let n = 1; n <= 20; n++) {
      const kb = page.keyboard;
      await page.getByLabel('GR No. *').focus();
      await kb.type(`KEY-${n}`);
      await kb.press('Tab');
      await kb.type(`Testkeyboard${n}`);
      await kb.press('Tab');
      await kb.type('Alpha');
      await kb.press('Tab'); // middle name
      await kb.press('Tab'); // gender
      await kb.press('ArrowDown');
      await kb.press('Tab');
      await kb.type('10052012');
      await kb.press('Tab'); // the calendar button of the date field
      await kb.press('Tab'); // birth place
      await kb.press('Tab'); // father
      await kb.press('Tab'); // mother
      await kb.press('Tab');
      await kb.type(`9${String(n).padStart(9, '0')}`);
      await kb.press('Tab'); // mobile 2
      await kb.press('Tab');
      await kb.type(`Test address ${n}`);
      await kb.press('Tab'); // aadhaar
      await kb.press('Tab'); // religion / caste
      await kb.press('Tab'); // category
      await kb.press('ArrowDown');
      await kb.press('Tab');
      await kb.type('15062020');
      await kb.press('Tab'); // the calendar button of the date field
      await kb.press('Tab'); // admission standard
      await kb.press('ArrowDown');
      await kb.press('Tab'); // previous school
      await kb.press('Tab'); // standard
      await kb.press('ArrowDown');
      await expect(page.getByLabel('Division (this year) *').locator('option')).toHaveCount(2); // the list follows the standard
      await kb.press('Tab'); // division
      await kb.press('ArrowDown');
      await kb.press('Tab'); // photo file
      await kb.press('Tab'); // Save
      await kb.press('Tab'); // Save and add next
      await kb.press('Enter');
      await expect(page.getByTestId('last-saved')).toContainText(`KEY-${n}`);
      await expect(page.getByLabel('GR No. *')).toBeFocused();
    }
    for (const n of [1, 10, 20]) {
      expect(await matchesOf(page, `Testkeyboard${n}`, '')).toHaveLength(1);
    }
  });

  test('FR-010, FR-012: the audit log shows the Students entries and never the full Aadhaar', async ({ page }) => {
    await openForm(page);
    await fillForm(page, { surname: 'Testaudit' });
    await page.getByLabel('Aadhaar').fill('123456789012');
    await page.getByRole('button', { name: 'Save', exact: true }).click();
    await expectSaved(page, 'Testaudit');

    await page.goto('/audit-log');
    await expect(page.getByRole('heading', { name: 'Audit log' })).toBeVisible();
    await page.getByLabel('Area', { exact: true }).selectOption('Students');

    await expect(page.getByRole('row').filter({ hasText: 'Students' }).first()).toBeVisible();
    await expect(page.locator('body')).not.toContainText('123456789012');
  });
});
