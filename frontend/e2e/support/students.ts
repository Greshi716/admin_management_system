import { Page } from '@playwright/test';
import { callApi, ensureCurrentYear } from './master-data';

/** Synthetic values only (constitution VI): made-up names, numbers that belong to nobody. */
export interface StudentInput {
  grNo: string;
  surname: string;
  firstName: string;
  middleName?: string;
  gender: 'Male' | 'Female' | 'Other';
  dob: string;
  mobile1: string;
  address: string;
  aadhaar?: string;
  religionCaste?: string;
  admissionDate: string;
  admissionStandardId: number;
  standardId: number;
  divisionId: number;
  categoryId: number;
}

/** The master data a student needs: the current year, one standard, one division of that year and a seeded category. */
export interface StudentMasterData {
  yearId: number;
  standardId: number;
  divisionId: number;
  categoryId: number;
}

/** Creates (or reuses) the current year, a standard, a division and picks the first category. Call after signInAsTestAdmin. */
export async function createStudentMasterData(page: Page, suffix = ''): Promise<StudentMasterData> {
  const year = await ensureCurrentYear(page);
  const standard = (
    await callApi(page, 'POST', '/api/standards', { name: `Test Standard ${suffix || Date.now()}`, displayOrder: 1 })
  ).body;
  const division = (
    await callApi(page, 'POST', '/api/divisions', { academicYearId: year.id, standardId: standard.id, name: 'A' })
  ).body;
  const categories = (await callApi(page, 'POST', '/api/student-categories/search', { first: 0, rows: 100, filters: {} }))
    .body.rows;
  return { yearId: year.id, standardId: standard.id, divisionId: division.id, categoryId: categories[0].id };
}

/** A valid input; `n` makes the GR No., name and numbers different from other inputs. */
export function studentInput(n: number, master: StudentMasterData): StudentInput {
  return {
    grNo: `E2E-${n}`,
    surname: `Testsurname${n}`,
    firstName: 'Alpha',
    middleName: 'Middle',
    gender: n % 2 === 0 ? 'Male' : 'Female',
    dob: '2012-05-10',
    mobile1: `9${String(n).padStart(9, '0')}`,
    address: `Test address ${n}, Test Town`,
    admissionDate: '2020-06-15',
    admissionStandardId: master.standardId,
    standardId: master.standardId,
    divisionId: master.divisionId,
    categoryId: master.categoryId,
  };
}

/** Registers a student through the API (POST /api/students) and returns the created record. Call after signInAsTestAdmin. */
export async function createStudent(page: Page, input: StudentInput): Promise<{ id: number; version: number; [key: string]: unknown }> {
  const result = await callApi(page, 'POST', '/api/students', input);
  if (result.status !== 201 && result.status !== 200) {
    throw new Error(`creating student ${input.grNo} failed with HTTP ${result.status}: ${JSON.stringify(result.body)}`);
  }
  return result.body;
}
