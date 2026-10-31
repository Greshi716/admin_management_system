import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface PendingStandard {
  standardId: number;
  name: string;
  pending: number;
}

export interface YearChangeStatus {
  active: boolean;
  newYear: string | null;
  oldYear: string | null;
  standards: PendingStandard[];
}

export interface StandardRef {
  id: number;
  name: string;
  usesStream: boolean;
}

export interface DivisionRef {
  id: number;
  name: string;
  standardId: number;
}

export interface CategoryRef {
  id: number;
  name: string;
}

export interface StudentItem {
  studentId: number;
  enrollmentId: number;
  grNo: string;
  name: string;
  division: string | null;
  category: string;
  unpaidYearFee: string;
  defaultOutcome: 'PROMOTED' | 'DETAINED' | 'LEFT';
  defaultDivisionId: number | null;
  defaultStream: string | null;
  version: number;
}

export interface PromoteScreen {
  fromYear: string;
  toYear: string;
  nextStandard: StandardRef | null;
  streamChoices: string[];
  divisions: DivisionRef[];
  categories: CategoryRef[];
  students: StudentItem[];
}

export interface PromoteLine {
  enrollmentId: number;
  outcome: 'PROMOTED' | 'DETAINED' | 'LEFT';
  divisionId: number | null;
  categoryId: number | null;
  stream: string | null;
  version: number;
}

export interface PromoteInput {
  lines: PromoteLine[];
}

export interface PromoteResult {
  runId: number;
  promoted: number;
  detained: number;
  left: number;
  carriedTotal: string;
}

@Injectable({ providedIn: 'root' })
export class YearChangeService {
  private readonly http = inject(HttpClient);

  getStatus(): Observable<YearChangeStatus> {
    return this.http.get<YearChangeStatus>('/api/year-change/status');
  }

  getPromoteScreen(standardId: number): Observable<PromoteScreen> {
    return this.http.get<PromoteScreen>(`/api/year-change/standards/${standardId}/students`);
  }

  promote(standardId: number, input: PromoteInput): Observable<PromoteResult> {
    return this.http.post<PromoteResult>(`/api/year-change/standards/${standardId}/promote`, input);
  }
}
