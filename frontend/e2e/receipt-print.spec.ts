import { expect, Page, test } from '@playwright/test';
import { addFeeRow, createFeeMasterData, FeeMasterData, finaliseYear } from './support/fees';
import { clearMasterData } from './support/master-data';
import { clearReceipts, markChequeNotCleared, recordReceipt } from './support/receipts';
import { createStudent, studentInput } from './support/students';
import { signInAsTestAdmin } from './support/test-data';

// Synthetic values only (constitution VI). Quickstart scenario 16: the print view in print media.
// Standard 1: 36,500.00 with a registration fee of 500.00.

interface Seed {
  master: FeeMasterData;
  studentId: number;
}

const now = new Date();
const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;

async function seed(page: Page): Promise<Seed> {
  const master = await createFeeMasterData(page, 1);
  await addFeeRow(page, master.yearId, { standardId: master.standards[0].id, amount: 36500, registrationFee: 500 });
  const standard = master.standards[0];
  const student = await createStudent(
    page,
    studentInput(1, { yearId: master.yearId, standardId: standard.id, divisionId: standard.divisionId, categoryId: master.categories[0].id }),
  );
  await finaliseYear(page, master.yearId);
  return { master, studentId: student.id };
}

test.describe('receipt print view', () => {
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

  test('FR-017, FR-018: in print media the page shows the receipt without the shell or buttons, and a second print is identical (scenario 16)', async ({
    page,
  }) => {
    const receipt = await recordReceipt(page, { studentId: data.studentId, receiptNo: '3100', receiptDate: today, registrationPaid: 500, feePaid: 10000 });

    await page.goto(`/finance/receipts/${receipt.serialNo}`);
    await page.getByTestId('print-receipt').click();
    await expect(page).toHaveURL(new RegExp(`/finance/receipts/${receipt.serialNo}/print$`));
    await expect(page.getByTestId('receipt-sheet')).toBeVisible();

    await page.emulateMedia({ media: 'print' });
    await expect(page.locator('header').first()).toBeHidden();
    await expect(page.getByTestId('print')).toBeHidden();
    const sheet = page.getByTestId('receipt-sheet');
    await expect(sheet).toBeVisible();
    for (const [id, text] of [
      ['print-receipt-no', '3100'],
      ['print-student', 'Test'],
      ['print-gr', 'E2E-1'],
      ['print-total', '10,500.00'],
      ['print-words', 'Ten Thousand Five Hundred Rupees Only'],
    ] as const) {
      await expect(page.getByTestId(id)).toContainText(text);
    }
    await expect(page.getByTestId('print-signature')).toBeVisible();
    await expect(sheet).not.toContainText('DUPLICATE');
    await expect(sheet).not.toContainText(/paid up to/i);
    const first = await sheet.innerText();

    await page.reload();
    await page.emulateMedia({ media: 'print' });
    await expect(page.getByTestId('receipt-sheet')).toBeVisible();
    expect(await page.getByTestId('receipt-sheet').innerText()).toBe(first);
  });

  test('FR-017: the page is A4 (the print stylesheet sets the size)', async ({ page }) => {
    const receipt = await recordReceipt(page, { studentId: data.studentId, receiptNo: '3101', receiptDate: today, feePaid: 100 });
    await page.goto(`/finance/receipts/${receipt.serialNo}/print`);
    await expect(page.getByTestId('receipt-sheet')).toBeVisible();

    const pageRule = await page.evaluate(() =>
      Array.from(document.styleSheets).flatMap((sheet) => Array.from(sheet.cssRules).map((rule) => rule.cssText)).find((css) => css.startsWith('@page')),
    );
    expect(pageRule?.toLowerCase()).toContain('size: a4');
  });

  test('FR-017: a Cheque not cleared receipt prints its status (scenario 16)', async ({ page }) => {
    const receipt = await recordReceipt(page, {
      studentId: data.studentId,
      receiptNo: '3300',
      receiptDate: today,
      feePaid: 5000,
      paymentMode: 'CHEQUE',
      chequeNo: '778899',
      chequeDate: today,
      bankName: 'Test Bank',
    });
    await markChequeNotCleared(page, data.studentId, receipt.serialNo);

    await page.goto(`/finance/receipts/${receipt.serialNo}/print`);
    await page.emulateMedia({ media: 'print' });
    await expect(page.getByTestId('print-status')).toHaveText('Cheque not cleared');
    await expect(page.getByTestId('print-mode')).toContainText('778899');
  });
});
