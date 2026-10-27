import { Page } from '@playwright/test';
import { callApi, ensureCurrentYear } from './master-data';

/**
 * Helpers for the fee specs, in the style of students.ts. They call the same API as the screens (see
 * specs/003-fees/contracts/openapi.yaml) and the test-profile endpoints. Synthetic values only (constitution VI).
 * Amounts are numbers with at most two decimals; the server returns totals, so no spec adds amounts itself.
 */

/** A fee row as the API returns it. */
export interface FeeRow {
  id: number;
  version: number;
  standardId: number;
  standardName: string;
  categoryId: number | null;
  categoryName: string;
  amount: number;
  registrationFee: number | null;
  dueDate: string | null;
}

/** The structure of one year as the API returns it. `version` is null while the year has no status row. */
export interface FeeStructure {
  yearId: number;
  yearName: string;
  status: 'Draft' | 'Finalised';
  version: number | null;
  studentCount: number;
  rows: FeeRow[];
}

/** What a fee row request carries. `categoryId` null means all categories of the standard. */
export interface FeeRowInput {
  standardId: number;
  categoryId?: number | null;
  amount: number;
  registrationFee?: number | null;
  dueDate?: string | null;
}

/** The fee master data of a spec: the year that covers today, standards (display order 1 to n) with a division A, and the seeded categories. */
export interface FeeMasterData {
  yearId: number;
  standards: { id: number; name: string; divisionId: number }[];
  categories: { id: number; code: string; name: string }[];
}

/**
 * Makes sure the year around today exists and creates `standardCount` standards with a division A each. Call after
 * signInAsTestAdmin and clearMasterData. `suffix` keeps standard names unique inside one spec.
 */
export async function createFeeMasterData(page: Page, standardCount = 2, suffix = ''): Promise<FeeMasterData> {
  const year = await ensureCurrentYear(page);
  const standards: FeeMasterData['standards'] = [];
  for (let i = 1; i <= standardCount; i++) {
    const standard = (
      await callApi(page, 'POST', '/api/standards', { name: `Fee Std ${suffix}${i}`, displayOrder: i })
    ).body;
    const division = (
      await callApi(page, 'POST', '/api/divisions', { academicYearId: year.id, standardId: standard.id, name: 'A' })
    ).body;
    standards.push({ id: standard.id, name: standard.name, divisionId: division.id });
  }
  const categories = (await callApi(page, 'POST', '/api/student-categories/search', { first: 0, rows: 100, filters: {} }))
    .body.rows;
  return { yearId: year.id, standards, categories };
}

/** Reads the structure of a year. */
export async function getFeeStructure(page: Page, yearId: number): Promise<FeeStructure> {
  const result = await callApi(page, 'GET', `/api/fee-structures/${yearId}`);
  if (result.status !== 200) {
    throw new Error(`reading the fee structure of year ${yearId} failed with HTTP ${result.status}`);
  }
  return result.body;
}

/** Adds a row. Sends the structure version the structure has now, as the screen does. */
export async function addFeeRow(page: Page, yearId: number, input: FeeRowInput, confirm = false): Promise<FeeRow> {
  const structure = await getFeeStructure(page, yearId);
  const result = await callApi(
    page,
    'POST',
    `/api/fee-structures/${yearId}/rows${confirm ? '?confirm=true' : ''}`,
    { ...input, structureVersion: structure.version },
  );
  if (result.status !== 201) {
    throw new Error(`adding a fee row failed with HTTP ${result.status}: ${JSON.stringify(result.body)}`);
  }
  return result.body;
}

/** Finalises the year with the structure version it has now. Returns the finalised structure. */
export async function finaliseYear(page: Page, yearId: number): Promise<FeeStructure> {
  const structure = await getFeeStructure(page, yearId);
  const result = await callApi(page, 'POST', `/api/fee-structures/${yearId}/finalise`, { version: structure.version });
  if (result.status !== 200) {
    throw new Error(`finalising year ${yearId} failed with HTTP ${result.status}: ${JSON.stringify(result.body)}`);
  }
  return result.body;
}

/**
 * Seeds `count` synthetic students with one class record each in the year, standard and division (test-profile
 * endpoint). Categories are spread over all categories unless `categoryId` is given. GR Nos. are `${grPrefix}-1`...
 */
export async function seedClassRecords(
  page: Page,
  options: { yearId: number; standardId: number; divisionId: number; count: number; categoryId?: number; grPrefix?: string },
): Promise<number> {
  const query = new URLSearchParams({
    academicYearId: String(options.yearId),
    standardId: String(options.standardId),
    divisionId: String(options.divisionId),
    count: String(options.count),
    grPrefix: options.grPrefix ?? 'SEED',
  });
  if (options.categoryId !== undefined) {
    query.set('categoryId', String(options.categoryId));
  }
  const result = await callApi(page, 'POST', `/api/test-support/seed-class-records?${query.toString()}`);
  if (result.status !== 200) {
    throw new Error(`seeding class records failed with HTTP ${result.status}: ${JSON.stringify(result.body)}`);
  }
  return result.body.created;
}

/** Empties the fee tables (test-profile endpoint); call before clearing students or master data. */
export async function clearFees(page: Page): Promise<void> {
  await callApi(page, 'POST', '/api/test-support/clear-fees');
}
