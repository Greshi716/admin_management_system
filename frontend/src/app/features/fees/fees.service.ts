import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiProblem, problemOf, STALE_VERSION } from '../../core/http/api-problem';

export const REVISE_CONFIRMATION_REQUIRED = 'REVISE_CONFIRMATION_REQUIRED';

/** A fee row as the API returns it (contracts/openapi.yaml `FeeRow`). Amounts are numbers; the server adds them. */
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

export interface FeeStructure {
  yearId: number;
  yearName: string;
  status: 'Draft' | 'Finalised';
  /** Null while the year has no structure yet. */
  version: number | null;
  studentCount: number;
  rows: FeeRow[];
}

export interface FeeChoices {
  /** An inactive one is listed only so the row that holds it can show it; it is not offered for other rows. */
  standards: { id: number; name: string; displayOrder: number; active: boolean }[];
  categories: { id: number; code: string; name: string; active: boolean }[];
}

/** What a row request carries. `categoryId` null means all categories of the standard. */
export interface FeeRowInput {
  /** Required when editing. */
  version?: number;
  /** The structure version the screen loaded. */
  structureVersion: number | null;
  standardId: number;
  categoryId: number | null;
  amount: number | null;
  registrationFee: number | null;
  dueDate: string | null;
}

export interface BroughtForward {
  version: number | null;
  amount: number;
  note: string | null;
}

export interface BroughtForwardInput {
  version: number | null;
  amount: number;
  note: string | null;
}

export type StudentFeeState = 'FEE_SET' | 'FEE_NOT_SET' | 'NOT_FINALISED';

export interface StudentFees {
  yearId: number | null;
  yearName: string | null;
  state: StudentFeeState;
  yearFee: number | null;
  registrationFee: number | null;
  totalFee: number | null;
  dueDate: string | null;
  broughtForward: BroughtForward;
  totalDue: number;
}

/** The answer 409 REVISE_CONFIRMATION_REQUIRED carries: nothing was changed, the admin must confirm. */
export class ReviseConfirmationRequired {
  constructor(readonly studentCount: number) {}
}

/** A save refused because the row or the year structure changed after the screen loaded; `latest` is the structure now. */
export class StaleStructure {
  constructor(readonly latest: unknown) {}
}

/** The typed meaning of a failed fee call; null when the error is none of the two special 409 answers. */
export function feeConflictOf(error: unknown): ReviseConfirmationRequired | StaleStructure | null {
  if (!(error instanceof HttpErrorResponse)) {
    return null;
  }
  const problem: ApiProblem | null = problemOf(error.error);
  if (problem?.code === REVISE_CONFIRMATION_REQUIRED) {
    const count = (problem.current as { studentCount?: number } | undefined)?.studentCount;
    return new ReviseConfirmationRequired(typeof count === 'number' ? count : 0);
  }
  if (problem?.code === STALE_VERSION) {
    return new StaleStructure(problem.current);
  }
  return null;
}

/** Calls of the fee structure screen, the profile Fees block and the brought-forward amount (F2). */
@Injectable({ providedIn: 'root' })
export class FeesService {
  private readonly http = inject(HttpClient);

  structure(yearId: number): Observable<FeeStructure> {
    return this.http.get<FeeStructure>(`/api/fee-structures/${yearId}`);
  }

  choices(yearId: number): Observable<FeeChoices> {
    return this.http.get<FeeChoices>(`/api/fee-structures/${yearId}/choices`);
  }

  /** `confirm` is true only after the admin saw the revise warning (FR-004). */
  addRow(yearId: number, input: FeeRowInput, confirm = false): Observable<FeeRow> {
    return this.http.post<FeeRow>(`/api/fee-structures/${yearId}/rows`, input, { params: confirmParams(confirm) });
  }

  editRow(yearId: number, rowId: number, input: FeeRowInput, confirm = false): Observable<FeeRow> {
    return this.http.put<FeeRow>(`/api/fee-structures/${yearId}/rows/${rowId}`, input, {
      params: confirmParams(confirm),
    });
  }

  deleteRow(
    yearId: number,
    rowId: number,
    version: number,
    structureVersion: number | null,
    confirm = false,
  ): Observable<void> {
    let params = new HttpParams().set('version', version);
    if (structureVersion !== null) {
      params = params.set('structureVersion', structureVersion);
    }
    if (confirm) {
      params = params.set('confirm', true);
    }
    return this.http.delete<void>(`/api/fee-structures/${yearId}/rows/${rowId}`, { params });
  }

  finalise(yearId: number, version: number | null): Observable<FeeStructure> {
    return this.http.post<FeeStructure>(`/api/fee-structures/${yearId}/finalise`, { version });
  }

  studentFees(studentId: number, yearId?: number): Observable<StudentFees> {
    const params = yearId === undefined ? new HttpParams() : new HttpParams().set('yearId', yearId);
    return this.http.get<StudentFees>(`/api/students/${studentId}/fees`, { params });
  }

  saveBroughtForward(studentId: number, input: BroughtForwardInput): Observable<BroughtForward> {
    return this.http.put<BroughtForward>(`/api/students/${studentId}/brought-forward`, input);
  }
}

function confirmParams(confirm: boolean): HttpParams {
  return confirm ? new HttpParams().set('confirm', true) : new HttpParams();
}
