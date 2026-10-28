import { expect, Page, test } from '@playwright/test';
import { addFeeRow, createFeeMasterData, finaliseYear } from './support/fees';
import { callApi, clearMasterData } from './support/master-data';
import { createStudent, studentInput } from './support/students';
import { signInAsTestAdmin } from './support/test-data';

// Synthetic values only (constitution VI). Test plan X-GUJ-1 and FR-007-B3: a Gujarati brought-forward note is kept
// and shown unchanged, and never appears in the audit log.

const NOTE = 'ગયા વર્ષની બાકી ફી';

async function seed(page: Page): Promise<number> {
  const master = await createFeeMasterData(page, 1);
  await addFeeRow(page, master.yearId, { standardId: master.standards[0].id, amount: 28000 });
  const student = await createStudent(
    page,
    studentInput(1, {
      yearId: master.yearId,
      standardId: master.standards[0].id,
      divisionId: master.standards[0].divisionId,
      categoryId: master.categories[0].id,
    }),
  );
  await finaliseYear(page, master.yearId);
  return student.id;
}

test.describe('brought-forward note in Gujarati', () => {
  test('FR-007: the note is saved, shown on the edit screen and the profile unchanged, and kept out of the audit log', async ({
    page,
  }) => {
    await signInAsTestAdmin(page);
    await clearMasterData(page);
    const id = await seed(page);

    await page.goto(`/students/${id}/edit`);
    const section = page.getByTestId('brought-forward-section');
    await section.getByLabel('Amount brought forward').fill('150.50');
    await section.getByLabel('Note').fill(NOTE);
    await section.getByRole('button', { name: 'Save brought forward' }).click();
    await expect(page.getByText('The brought-forward amount was saved.')).toBeVisible();

    await page.reload();
    await expect(page.getByTestId('brought-forward-section').getByLabel('Note')).toHaveValue(NOTE);
    await page.goto(`/students/${id}`);
    await expect(page.getByTestId('fee-brought-forward-note')).toHaveText(NOTE);

    const log = (await callApi(page, 'POST', '/api/audit-log/search', { first: 0, rows: 50, filters: {} })).body.rows;
    expect(JSON.stringify(log)).not.toContain(NOTE);
  });
});
