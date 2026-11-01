import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable, OnDestroy, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { TablePage } from '../../shared/table/table-types';

export interface AdmissionRow {
  id: number;
  submittedAt: string | null;
  status: 'Pending' | 'Approved' | 'Rejected';
  duplicateFlag: boolean;
  duplicateOf: Record<string, unknown> | null;
  studentName: string;
  surname: string;
  firstName: string;
  middleName: string | null;
  gender: string;
  dob: string;
  mobile1: string;
  admissionStandard: string;
  admissionStandardId: number;
  form: Record<string, unknown>;
  remarks: string | null;
  studentId: number | null;
  reviewedBy: number | null;
  reviewedAt: string | null;
  version: number;
}

export interface DuplicateMatch {
  type: 'Student' | 'Request';
  id: number;
  label: string;
  link: string;
}

export interface AdmissionDetail {
  id: number;
  status: 'Pending' | 'Approved' | 'Rejected';
  duplicateFlag: boolean;
  duplicateOf: Record<string, unknown> | null;
  duplicateMatches: DuplicateMatch[];
  form: Record<string, unknown>;
  version: number;
  submittedAt: string | null;
  remarks: string | null;
  studentId: number | null;
  reviewedBy: number | null;
  reviewedAt: string | null;
}

export interface ImportInvalidRow {
  row: number;
  reason: string;
}

export interface ImportResult {
  batchId: number;
  total: number;
  added: number;
  skipped: number;
  duplicates: number;
  invalid: ImportInvalidRow[];
}

export interface ApproveInput {
  grNo: string;
  academicYearId: number;
  divisionId: number;
  categoryId?: number;
  admissionDate?: string;
  form?: Record<string, unknown>;
  version: number;
}

export interface RejectInput {
  remarks: string;
  version: number;
}

export interface AcademicYearOption {
  id: number;
  name: string;
  startDate: string;
  endDate: string;
}

@Injectable({ providedIn: 'root' })
export class AdmissionsService implements OnDestroy {
  private readonly http = inject(HttpClient);

  readonly pendingCount = signal<number>(0);
  private timerId: ReturnType<typeof setInterval> | null = null;

  constructor() {
    this.refreshPendingCount();
    // Poll every 60 seconds (FR-036)
    this.timerId = setInterval(() => {
      this.refreshPendingCount();
    }, 60_000);
  }

  ngOnDestroy(): void {
    if (this.timerId) {
      clearInterval(this.timerId);
      this.timerId = null;
    }
  }

  refreshPendingCount(): void {
    this.http.get<{ pending: number }>('/api/admissions/pending-count').subscribe({
      next: (res) => this.pendingCount.set(res.pending),
      error: () => {},
    });
  }

  list(status?: string, duplicate?: boolean, first = 0, rows = 25, search?: string): Observable<TablePage> {
    let params = new HttpParams().set('first', first).set('rows', rows);
    if (status && status !== 'All') {
      params = params.set('status', status);
    }
    if (duplicate !== undefined && duplicate !== null) {
      params = params.set('duplicate', duplicate);
    }
    if (search && search.trim()) {
      params = params.set('search', search.trim());
    }
    return this.http.get<TablePage>('/api/admissions', { params });
  }

  getDetail(id: number): Observable<AdmissionDetail> {
    return this.http.get<AdmissionDetail>(`/api/admissions/${id}`);
  }

  approve(id: number, input: ApproveInput): Observable<Record<string, unknown>> {
    return this.http.post<Record<string, unknown>>(`/api/admissions/${id}/approve`, input).pipe(
      tap(() => this.refreshPendingCount())
    );
  }

  reject(id: number, input: RejectInput): Observable<Record<string, unknown>> {
    return this.http.post<Record<string, unknown>>(`/api/admissions/${id}/reject`, input).pipe(
      tap(() => this.refreshPendingCount())
    );
  }

  upload(file: File): Observable<ImportResult> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<ImportResult>('/api/admissions/upload', formData).pipe(
      tap(() => this.refreshPendingCount())
    );
  }

  getAcademicYears(): Observable<AcademicYearOption[]> {
    return this.http.get<AcademicYearOption[]>('/api/academic-years');
  }
}
