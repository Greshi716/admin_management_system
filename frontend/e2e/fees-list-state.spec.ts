import { expect, Page, test } from '@playwright/test';
import { addFeeRow, createFeeMasterData, FeeMasterData, finaliseYear } from './support/fees';
import { clearMasterData } from './support/master-data';
import { createStudent, studentInput } from './support/students';
import { signInAsTestAdmin } from './support/test-data';

// Synthetic values only (constitution VI). FR-010: the Total fee cell of the Students list shows the amount, "Fee not
// set" or "Not finalised"; Fee state is a hidden column (the Excel export carries it, decision log 2026-10-28).

/** Standard 1 has a 28,000.00 row, standard 2 has none. E2E-1 is in standard 1, E2E-2 in standard 2. */
async function seed(page: Page): Promise<FeeMasterData> {
  const master = await createFeeMasterData(page, 2);
  await addFeeRow(page, master.yearId, { standardId: master.standards[0].id, amount: 28000 });
  for (const [n, index] of [[1, 0], [2, 1]] as const) {
    await createStudent(
      page,
      studentInput(n, {
        yearId: master.yearId,
        standardId: master.standards[index].id,
        divisionId: master.standards[index].divisionId,
        categoryId: master.categories[0].id,
      }),
    );
  }
  return master;
}

async function openList(page: Page): Promise<void> {
  await page.goto('/students');
  await expect(page.getByRole('heading', { name: 'Students', exact: true })).toBeVisible();
  await expect(page.locator('tbody tr')).toHaveCount(2);
}

/** The trimmed text of one cell, found by the column header so the spec does not depend on the column order. */
async function cell(page: Page, grNo: string, header: string): Promise<string> {
  const headers = (await page.locator('thead th').allInnerTexts()).map((text) => text.trim());
  const index = headers.findIndex((text) => text.toLowerCase().includes(header.toLowerCase()));
  expect(index, `column "${header}" is in the table header`).toBeGreaterThanOrEqual(0);
  return (await page.locator('tbody tr', { hasText: grNo }).locator('td').nth(index).innerText()).trim();
}

test.describe('Students list: Total fee cell', () => {
  let master: FeeMasterData;

  test.beforeEach(async ({ page }) => {
    await signInAsTestAdmin(page);
    await clearMasterData(page);
    master = await seed(page);
  });

  test('FR-010: a Draft year shows "Not finalised" in the Total fee cell and no Fee state column', async ({ page }) => {
    await openList(page);

    for (const grNo of ['E2E-1', 'E2E-2']) {
      expect(await cell(page, grNo, 'Total fee')).toBe('Not finalised');
    }
    await expect(page.locator('thead th', { hasText: 'Fee state' })).toHaveCount(0);
  });

  test('FR-010: after finalising, the Total fee cell shows the amount, or "Fee not set" where the standard has no row', async ({
    page,
  }) => {
    await finaliseYear(page, master.yearId);
    await openList(page);

    expect(await cell(page, 'E2E-1', 'Total fee')).toBe('28,000.00');
    expect(await cell(page, 'E2E-2', 'Total fee')).toBe('Fee not set');
  });
});
