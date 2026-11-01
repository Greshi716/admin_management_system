import { expect, Page, test } from '@playwright/test';
import { callApi, clearMasterData, ensureCurrentYear } from './support/master-data';
import { signInAsTestAdmin } from './support/test-data';

/**
 * Admissions e2e flow (FR-028, FR-030, FR-031, FR-032, FR-033, FR-034, FR-036, FR-037):
 * Upload responses file -> duplicate highlighted -> approve one -> reject one -> badge changes -> upload again skips.
 */
test.describe('Admissions Google Form flow (FR-037)', () => {
  test.beforeEach(async ({ page }) => {
    await signInAsTestAdmin(page);
    await clearMasterData(page);
  });

  test('upload, duplicate highlighted, approve, reject, badge changes, upload again skips', async ({ page }) => {
    // 1. Setup master data: standard "Standard 1" and division "A" in current year
    const currentYear = await ensureCurrentYear(page);

    const standard = (
      await callApi(page, 'POST', '/api/standards', {
        name: 'Standard 1',
        displayOrder: 1,
      })
    ).body;

    await callApi(page, 'POST', '/api/divisions', {
      academicYearId: currentYear.id,
      standardId: standard.id,
      name: 'A',
    });

    // 2. Navigate to Admissions via top bar Approvals tab
    await page.goto('/');
    const approvalsTab = page.getByRole('link', { name: /Approvals/ });
    await expect(approvalsTab).toBeVisible();
    await approvalsTab.click();

    await expect(page).toHaveURL(/\/admissions/);
    await expect(page.getByRole('heading', { name: 'Admissions' })).toBeVisible();

    // 3. Open Upload Dialog
    const uploadBtn = page.getByTestId('upload-responses-btn');
    await expect(uploadBtn).toBeVisible();
    await uploadBtn.click();

    await expect(page.getByRole('heading', { name: 'Upload responses file' })).toBeVisible();

    // Synthetic CSV responses content:
    // Row 1: Patil, Rahul (Valid new)
    // Row 2: Patil, Rahul (Valid duplicate of Row 1)
    // Row 3: Deshmukh, Sneha (Valid new, will be rejected)
    // Row 4: Missing surname (Invalid)
    const csvContent = [
      'Timestamp,Surname,First name,Father\'s / middle name,Gender,Date of birth,Place of birth,Father\'s name,Mother\'s name,Mobile 1,Mobile 2,Address,Aadhaar number,Religion / Caste,Category,Previous school,Admission date,Admission standard',
      '2026/05/01 09:30:00,Patil,Rahul,Ashok,Male,12/04/2015,Pune,Ashok,Sunita,9876543210,,"123 Main Street, Pune",123456789012,Hindu,General,St Mary,01/06/2026,Standard 1',
      '2026/05/01 10:15:00,Patil,Rahul,Ashok,Male,12/04/2015,Pune,Ashok,Sunita,9876543210,,"123 Main Street, Pune",123456789012,Hindu,General,,01/06/2026,Standard 1',
      '2026/05/01 11:00:00,Deshmukh,Sneha,Pravin,Female,15/07/2015,Mumbai,Pravin,Rekha,9811223344,,"45 MG Road, Mumbai",,Hindu,General,,01/06/2026,Standard 1',
      '2026/05/01 11:30:00,,Aarav,Suresh,Male,15/06/2014,Mumbai,Suresh,Meena,9822334455,,"45 MG Road, Mumbai",,Hindu,General,,,Standard 1',
    ].join('\n');

    // Attach synthetic file
    await page.locator('#responses-file-input').setInputFiles({
      name: 'synthetic-responses.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from(csvContent, 'utf-8'),
    });

    // Click Upload
    await page.getByTestId('upload-submit-btn').click();

    // Verify upload results
    const summary = page.getByTestId('upload-result-summary');
    await expect(summary).toBeVisible();
    await expect(page.getByTestId('result-total')).toHaveText('4');
    await expect(page.getByTestId('result-added')).toHaveText('3');
    await expect(page.getByTestId('result-duplicates')).toHaveText('1');

    // Close upload dialog
    await page.getByTestId('upload-done-btn').click();
    await expect(summary).not.toBeVisible();

    // 4. Check badge shows 3 pending entries
    const badge = page.getByTestId('approvals-badge');
    await expect(badge).toBeVisible();
    await expect(badge).toHaveText('3');

    // 5. Verify waiting list rows & duplicate badge
    const duplicateBadges = page.getByTestId('duplicate-badge');
    await expect(duplicateBadges).toHaveCount(1);

    // 6. Approve one entry (Patil, Rahul)
    // Find the first review button
    const reviewButtons = page.getByRole('link', { name: 'Review' });
    await expect(reviewButtons.first()).toBeVisible();
    await reviewButtons.first().click();

    await expect(page).toHaveURL(/\/admissions\/\d+/);
    await expect(page.getByTestId('input-gr-no')).toBeVisible();

    // Type GR No.
    await page.getByTestId('input-gr-no').fill('GR-ADM-001');

    // Click Approve admission
    await page.getByTestId('approve-btn').click();

    // Confirm dialog
    const confirmApproveBtn = page.getByTestId('confirm-approve-btn');
    await expect(confirmApproveBtn).toBeVisible();
    await confirmApproveBtn.click();

    // Navigates to student profile
    await expect(page).toHaveURL(/\/students\/\d+/);

    // 7. Check Approvals badge decremented from 3 to 2
    await expect(badge).toHaveText('2');

    // 8. Go back to Admissions and Reject an entry
    await page.getByRole('link', { name: /Approvals/ }).click();
    await expect(page).toHaveURL(/\/admissions/);

    // Filter to Pending
    await page.getByTestId('status-filter-pending').click();

    // Open review on Deshmukh, Sneha
    const secondReview = page.getByRole('link', { name: 'Review' }).first();
    await secondReview.click();

    await expect(page.getByTestId('reject-btn')).toBeVisible();
    await page.getByTestId('reject-btn').click();

    // Provide remarks
    await expect(page.getByTestId('reject-remarks-textarea')).toBeVisible();
    await page.getByTestId('reject-remarks-textarea').fill('Did not submit required birth certificate.');
    await page.getByTestId('confirm-reject-btn').click();

    // Verify rejected state
    await expect(page.getByTestId('rejected-banner')).toBeVisible();
    await expect(page.getByTestId('rejected-banner')).toContainText('Did not submit required birth certificate.');

    // 9. Check Approvals badge decremented from 2 to 1
    await expect(badge).toHaveText('1');

    // 10. Re-upload the same file -> verify skipped
    await page.getByRole('link', { name: /Approvals/ }).click();
    await page.getByTestId('upload-responses-btn').click();

    await page.locator('#responses-file-input').setInputFiles({
      name: 'synthetic-responses.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from(csvContent, 'utf-8'),
    });

    await page.getByTestId('upload-submit-btn').click();

    const secondSummary = page.getByTestId('upload-result-summary');
    await expect(secondSummary).toBeVisible();
    // All 3 valid rows already imported, so added: 0, skipped: 3
    await expect(page.getByTestId('result-added')).toHaveText('0');
    await expect(page.getByTestId('result-skipped')).toHaveText('3');

    await page.getByTestId('upload-done-btn').click();
  });
});
