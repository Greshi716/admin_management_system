import { expect, Page, test } from '@playwright/test';
import { addFeeRow, createFeeMasterData, FeeMasterData, finaliseYear } from './support/fees';
import { callApi, clearMasterData } from './support/master-data';
import { createStudent, studentInput } from './support/students';
import { signInAsTestAdmin } from './support/test-data';

// Synthetic values only (constitution VI). Test plan FR-004-N2 and SC-003: every row change warns again, and the
// warning count follows the number of class records of the year.

interface Seed {
  master: FeeMasterData;
  ids: number[];
}

async function register(page: Page, master: FeeMasterData, n: number, standardIndex: number): Promise<number> {
  const standard = master.standards[standardIndex];
  const student = await createStudent(
    page,
    studentInput(n, {
      yearId: master.yearId,
      standardId: standard.id,
      divisionId: standard.divisionId,
      categoryId: master.categories[0].id,
    }),
  );
  return student.id;
}

/** Standard 1: 30,000.00; standard 2: 36,000.00. Students 1 in standard 1, 2 and 3 in standard 2. Finalised. */
async function seedFinalised(page: Page): Promise<Seed> {
  const master = await createFeeMasterData(page, 2);
  await addFeeRow(page, master.yearId, { standardId: master.standards[0].id, amount: 30000 });
  await addFeeRow(page, master.yearId, { standardId: master.standards[1].id, amount: 36000 });
  const ids = [await register(page, master, 1, 0), await register(page, master, 2, 1), await register(page, master, 3, 1)];
  await finaliseYear(page, master.yearId);
  return { master, ids };
}

const rowAt = (page: Page, index: number) => page.getByTestId('fee-row').nth(index);

async function openRevising(page: Page): Promise<void> {
  await page.goto('/setup/fees');
  await expect(page.getByTestId('fee-card-title')).toBeVisible();
  await page.getByRole('button', { name: 'Revise', exact: true }).click();
  await expect(page.getByTestId('revise-notice')).toBeVisible();
}

async function yearFee(page: Page, studentId: number): Promise<number> {
  return (await callApi(page, 'GET', `/api/students/${studentId}/fees`)).body.yearFee;
}

test.describe('revise warns on every change', () => {
  let data: Seed;

  test.beforeEach(async ({ page }) => {
    await signInAsTestAdmin(page);
    await clearMasterData(page);
    data = await seedFinalised(page);
  });

  test('FR-004: a second row change shows the warning again and each change is recalculated', async ({ page }) => {
    await openRevising(page);

    await rowAt(page, 0).getByLabel('Yearly fee').fill('31000');
    await rowAt(page, 0).getByRole('button', { name: 'Save row' }).click();
    await expect(page.getByTestId('revise-warning')).toBeVisible();
    await expect(page.getByTestId('revise-count')).toContainText('3 students');
    await page.getByRole('button', { name: 'Confirm and recalculate' }).click();
    await expect(page.getByText(/Every student's fee was recalculated/)).toBeVisible();

    await openRevising(page);
    await rowAt(page, 1).getByLabel('Yearly fee').fill('37000');
    await rowAt(page, 1).getByRole('button', { name: 'Save row' }).click();
    await expect(page.getByTestId('revise-warning')).toBeVisible();
    await expect(page.getByTestId('revise-count')).toContainText('3 students');
    await page.getByRole('button', { name: 'Confirm and recalculate' }).click();
    await expect(page.getByText(/Every student's fee was recalculated/)).toBeVisible();

    expect(await yearFee(page, data.ids[0])).toBe(31000);
    expect(await yearFee(page, data.ids[1])).toBe(37000);
    expect(await yearFee(page, data.ids[2])).toBe(37000);
  });

  test('FR-004: after another student is registered the warning counts four and Cancel leaves every fee as it was', async ({
    page,
  }) => {
    const fourth = await register(page, data.master, 4, 1);
    await openRevising(page);

    await rowAt(page, 1).getByLabel('Yearly fee').fill('38000');
    await rowAt(page, 1).getByRole('button', { name: 'Save row' }).click();
    await expect(page.getByTestId('revise-count')).toContainText('4 students');
    await page.getByRole('dialog').getByRole('button', { name: 'Cancel' }).click();

    await expect(page.getByTestId('revise-warning')).toHaveCount(0);
    for (const id of [data.ids[1], data.ids[2], fourth]) {
      expect(await yearFee(page, id)).toBe(36000);
    }
  });
});
