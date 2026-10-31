import { expect, Page, test } from '@playwright/test';
import { addFeeRow, createFeeMasterData, FeeMasterData, finaliseYear } from './support/fees';
import { callApi, clearMasterData } from './support/master-data';
import { clearReceipts, getFeeCollection, setBroughtForward } from './support/receipts';
import { createStudent, studentInput } from './support/students';
import { academicYearAround, signInAsTestAdmin } from './support/test-data';

// Synthetic values only (constitution VI). Quickstart scenarios 9 to 11 and 14 (a Left student) through the screens.
// Standard 1: 36,500.00 with a registration fee of 500.00. Student D has 3,000.00 brought forward from an earlier year.

interface Seed {
  master: FeeMasterData;
  studentId: number;
  earlierYearName: string;
}

async function seed(page: Page): Promise<Seed> {
  const master = await createFeeMasterData(page, 1);
  await addFeeRow(page, master.yearId, { standardId: master.standards[0].id, amount: 36500, registrationFee: 500 });
  const earlier = academicYearAround(new Date(), -1);
  const created = await callApi(page, 'POST', '/api/academic-years', earlier);
  expect(created.status).toBe(201);
  const standard = master.standards[0];
  const student = await createStudent(
    page,
    studentInput(4, {
      yearId: master.yearId,
      standardId: standard.id,
      divisionId: standard.divisionId,
      categoryId: master.categories[0].id,
    }),
  );
  await finaliseYear(page, master.yearId);
  await setBroughtForward(page, student.id, 3000);
  return { master, studentId: student.id, earlierYearName: earlier.name };
}

const today = new Date();
const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

async function openForm(page: Page, studentId: number): Promise<void> {
  await page.goto(`/finance/fee-collection?studentId=${studentId}`);
  await expect(page.getByTestId('strip')).toBeVisible();
}

test.describe('previous-years receipts', () => {
  let data: Seed;

  test.beforeEach(async ({ page }) => {
    await signInAsTestAdmin(page);
    await clearReceipts(page);
    await clearMasterData(page);
    data = await seed(page);
  });

  test.afterEach(async ({ page }) => {
    await clearReceipts(page);
  });

  test('FR-004, FR-005: pay 2,000.00 of the 3,000.00 earlier years owe, then 1,500.00 is refused with 1,000.00 still due (scenarios 9, 10)', async ({
    page,
  }) => {
    await openForm(page, data.studentId);
    await expect(page.getByTestId('earlier-first')).toContainText('3,000.00');
    await expect(page.getByLabel('Receipt type')).toHaveValue('PREVIOUS_YEARS');
    await expect(page.getByLabel('Earlier academic year')).toContainText(data.earlierYearName);
    await expect(page.getByLabel('Registration fee')).toHaveCount(0);
    await expect(page.getByTestId('fee-due')).toContainText('3,000.00');

    await page.getByLabel('Receipt no. (from the book)').fill('3201');
    await page.getByLabel("Earlier years' fee").fill('2000');
    await expect(page.getByTestId('live-total')).toHaveText('2,000.00');

    await page.getByTestId('save').click();
    await expect(page.getByTestId('confirm-type')).toContainText('Previous years');
    await expect(page.getByTestId('confirm-type')).toContainText(data.earlierYearName);
    await expect(page.getByTestId('confirm-total')).toHaveText('2,000.00');
    await page.getByRole('button', { name: 'Confirm and save' }).click();
    await expect(page).toHaveURL(/\/finance\/receipts\/\d+$/);

    const after = await getFeeCollection(page, data.studentId);
    expect(after.balance['earlierYearsPending']).toBe(1000);
    expect(after.balance['previousYearsPaid']).toBe(2000);

    await openForm(page, data.studentId);
    await page.getByLabel('Receipt no. (from the book)').fill('3202');
    await page.getByLabel("Earlier years' fee").fill('1500');
    await page.getByTestId('save').click(); // the server's check refuses before the confirmation opens
    await expect(page.getByRole('alert').getByText('Only 1000.00 is still due')).toBeVisible();
    await expect(page.getByTestId('confirm-details')).toHaveCount(0);
    expect((await getFeeCollection(page, data.studentId)).receipts).toHaveLength(1);
  });

  test('FR-025: the brought-forward amount cannot go below what previous-years receipts total (scenario 11)', async ({ page }) => {
    await callApi(page, 'POST', '/api/receipts', {
      studentId: data.studentId,
      receiptType: 'PREVIOUS_YEARS',
      previousYearId: (await callApi(page, 'GET', '/api/academic-years')).body.find((y: { name: string }) => y.name === data.earlierYearName).id,
      receiptNo: '3211',
      receiptDate: iso(today),
      feePaid: 2000,
      paymentMode: 'CASH',
      balanceVersion: (await getFeeCollection(page, data.studentId)).balanceVersion,
    });

    await page.goto(`/students/${data.studentId}/edit`);
    const section = page.getByTestId('brought-forward-section');
    await expect(section).toBeVisible();
    await section.getByLabel('Amount brought forward').fill('1500');
    await section.getByRole('button', { name: 'Save brought forward' }).click();
    await expect(section).toContainText('already total 2000.00');

    await section.getByLabel('Amount brought forward').fill('2000');
    await section.getByRole('button', { name: 'Save brought forward' }).click();
    await expect(page.getByText('The brought-forward amount was saved.')).toBeVisible();
  });

  test('FR-007: a Left student can pay earlier years (scenario 14)', async ({ page }) => {
    const student = (await callApi(page, 'GET', `/api/students/${data.studentId}`)).body;
    expect((await callApi(page, 'PUT', `/api/students/${data.studentId}/left`, { version: student.version })).status).toBe(200);

    await openForm(page, data.studentId);
    await page.getByLabel('Receipt no. (from the book)').fill('3221');
    await page.getByLabel("Earlier years' fee").fill('500');
    await page.getByTestId('save').click();
    await page.getByRole('button', { name: 'Confirm and save' }).click();
    await expect(page).toHaveURL(/\/finance\/receipts\/\d+$/);
  });
});
