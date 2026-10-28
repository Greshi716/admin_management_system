import { expect, Page, test } from '@playwright/test';
import { addFeeRow, createFeeMasterData, FeeMasterData, finaliseYear } from './support/fees';
import { callApi, clearMasterData } from './support/master-data';
import { createStudent, studentInput } from './support/students';
import { signInAsTestAdmin } from './support/test-data';

// Synthetic values only (constitution VI). Quickstart scenarios 13 to 15.

const MESSAGE =
  'The fee structure for this year is finalised, so the standard and category cannot be changed. The division can.';

interface Seed {
  master: FeeMasterData;
  studentId: number;
  divisionB: number;
}

/** Standard 1 has a fee row, a division A and a division B; one student in standard 1 / A. Not yet finalised. */
async function seed(page: Page): Promise<Seed> {
  const master = await createFeeMasterData(page, 2);
  await addFeeRow(page, master.yearId, { standardId: master.standards[0].id, amount: 28000 });
  const divisionB = (
    await callApi(page, 'POST', '/api/divisions', {
      academicYearId: master.yearId,
      standardId: master.standards[0].id,
      name: 'B',
    })
  ).body.id;
  const student = await createStudent(
    page,
    studentInput(1, {
      yearId: master.yearId,
      standardId: master.standards[0].id,
      divisionId: master.standards[0].divisionId,
      categoryId: master.categories[0].id,
    }),
  );
  return { master, studentId: student.id, divisionB };
}

async function openDialog(page: Page, studentId: number) {
  await page.goto(`/students/${studentId}`);
  await page.getByRole('button', { name: /^Change class record/ }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByLabel('Division *')).toBeVisible();
  return dialog;
}

test.describe('fee lock on the class record', () => {
  let data: Seed;

  test.beforeEach(async ({ page }) => {
    await signInAsTestAdmin(page);
    await clearMasterData(page);
    data = await seed(page);
  });

  test('FR-008: in a finalised year standard and category are fixed with the message and the division can be changed (scenario 13)', async ({
    page,
  }) => {
    await finaliseYear(page, data.master.yearId);
    const dialog = await openDialog(page, data.studentId);

    await expect(dialog.getByTestId('fee-lock-notice')).toHaveText(MESSAGE);
    await expect(dialog.getByLabel('Standard *')).toBeDisabled();
    await expect(dialog.getByLabel('Category *')).toBeDisabled();
    await expect(dialog.getByLabel('Division *')).toBeEnabled();

    await dialog.getByLabel('Division *').selectOption({ label: 'B' });
    await dialog.getByRole('button', { name: 'Save' }).click();

    await expect(page.getByTestId('class-history')).toContainText('Fee Std 1-B');
  });

  test('FR-008: a standard or category change sent to the API is refused and nothing changes (scenario 14)', async ({ page }) => {
    await finaliseYear(page, data.master.yearId);
    const profile = (await callApi(page, 'GET', `/api/students/${data.studentId}`)).body;
    const record = profile.classHistory[0];

    const result = await callApi(page, 'PUT', `/api/students/${data.studentId}/enrollments/${data.master.yearId}`, {
      version: record.version,
      standardId: data.master.standards[1].id,
      divisionId: data.master.standards[1].divisionId,
      categoryId: record.categoryId,
    });

    expect(result.status).toBe(409);
    expect(result.body.code).toBe('FEE_STRUCTURE_FINALISED');
    const after = (await callApi(page, 'GET', `/api/students/${data.studentId}`)).body.classHistory[0];
    expect(after.standardId).toBe(record.standardId);
    expect(after.version).toBe(record.version);
  });

  test('FR-008: the year was finalised after the dialog opened - the save shows the same message (scenario 14, screen)', async ({
    page,
  }) => {
    const dialog = await openDialog(page, data.studentId);
    await expect(dialog.getByLabel('Standard *')).toBeEnabled();
    await finaliseYear(page, data.master.yearId);

    await dialog.getByLabel('Category *').selectOption({ index: 1 });
    await dialog.getByRole('button', { name: 'Save' }).click();

    await expect(dialog.getByTestId('fee-lock-notice')).toHaveText(MESSAGE);
    await expect(dialog.getByLabel('Category *')).toBeDisabled();
  });

  test('FR-008: in a Draft year standard and category can be changed as in F1 (scenario 15)', async ({ page }) => {
    const dialog = await openDialog(page, data.studentId);

    await expect(dialog.getByTestId('fee-lock-notice')).toHaveCount(0);
    await expect(dialog.getByLabel('Standard *')).toBeEnabled();
    await expect(dialog.getByLabel('Category *')).toBeEnabled();
    await dialog.getByLabel('Standard *').selectOption({ label: 'Fee Std 2' });
    await dialog.getByLabel('Division *').selectOption({ label: 'A' });
    await dialog.getByRole('button', { name: 'Save' }).click();

    await expect(page.getByTestId('class-history')).toContainText('Fee Std 2-A');
  });
});
