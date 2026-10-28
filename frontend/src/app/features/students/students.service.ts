import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

/** Request body of a registration or an edit (contracts/openapi.yaml `StudentInput`). */
export interface StudentInput {
  /** Required when editing. */
  version?: number;
  grNo: string;
  surname: string;
  firstName: string;
  middleName?: string | null;
  gender: 'Male' | 'Female' | 'Other' | '';
  dob: string;
  birthPlace?: string | null;
  fatherName?: string | null;
  motherName?: string | null;
  mobile1: string;
  mobile2?: string | null;
  address: string;
  aadhaar?: string | null;
  religionCaste?: string | null;
  /** Registration only; an edit ignores it. */
  categoryId?: number | null;
  admissionDate: string;
  admissionStandardId: number | null;
  previousSchool?: string | null;
  /** Registration only: the standard and division of the current year. */
  standardId?: number | null;
  divisionId?: number | null;
}

export interface Enrollment {
  version: number;
  academicYearId: number;
  academicYearName: string;
  standardId: number;
  divisionId: number;
  stdDiv: string;
  categoryId: number;
  categoryName: string;
  yearStatus: 'Studying' | 'Promoted' | 'Detained' | 'Left';
  /** True when the year's fee structure is finalised: standard and category are fixed, the division can change (F2 FR-008). */
  feeLocked: boolean;
}

export interface EnrollmentInput {
  version: number;
  standardId: number;
  divisionId: number;
  categoryId: number;
}

/** A student as the profile shows it: Aadhaar and religion/caste are masked. */
export interface Student {
  id: number;
  version: number;
  grNo: string;
  srNo: number;
  surname: string;
  firstName: string;
  middleName: string | null;
  gender: string;
  dob: string;
  birthPlace: string | null;
  fatherName: string | null;
  motherName: string | null;
  mobile1: string;
  mobile2: string | null;
  address: string;
  aadhaar: string | null;
  religionCaste: string | null;
  admissionDate: string;
  admissionStandardName: string;
  previousSchool: string | null;
  photoVersion: number | null;
  status: 'Active' | 'Left';
  source: 'Manual' | 'Google Form';
  classHistory: Enrollment[];
}

export interface NameMatch {
  id: number;
  grNo: string;
  name: string;
  status: string;
}

export interface PickLists {
  currentYear: { id: number; name: string } | null;
  standards: { id: number; name: string; displayOrder: number; stream: string | null; active: boolean }[];
  divisions: { id: number; standardId: number; name: string; active: boolean }[];
  categories: { id: number; code: string; name: string; active: boolean }[];
}

/** Every standard, division (of any year) and category, active or not, for the filters of the list. */
export interface FilterOptions {
  standards: PickLists['standards'];
  divisions: { id: number; standardId: number; name: string; yearId: number; yearName: string; active: boolean }[];
  categories: PickLists['categories'];
}

export interface HistoryEntry {
  at: string;
  userName: string | null;
  action: string;
  record: string;
  changes: Record<string, unknown> | null;
}

export interface HistoryResult {
  entries: HistoryEntry[];
  truncated: boolean;
}

/** The typed client for every students endpoint of the contract. */
@Injectable({ providedIn: 'root' })
export class StudentsService {
  private readonly http = inject(HttpClient);

  register(input: StudentInput): Observable<Student> {
    return this.http.post<Student>('/api/students', input);
  }

  get(id: number): Observable<Student> {
    return this.http.get<Student>(`/api/students/${id}`);
  }

  /** The edit form values, with the full Aadhaar and religion/caste. */
  editForm(id: number): Observable<StudentInput> {
    return this.http.get<StudentInput>(`/api/students/${id}/edit`);
  }

  update(id: number, input: StudentInput): Observable<Student> {
    return this.http.put<Student>(`/api/students/${id}`, input);
  }

  markLeft(id: number, version: number): Observable<Student> {
    return this.http.put<Student>(`/api/students/${id}/left`, { version });
  }

  updateEnrollment(id: number, yearId: number, input: EnrollmentInput): Observable<Enrollment> {
    return this.http.put<Enrollment>(`/api/students/${id}/enrollments/${yearId}`, input);
  }

  history(id: number): Observable<HistoryResult> {
    return this.http.get<HistoryResult>(`/api/students/${id}/history`);
  }

  nameMatches(surname: string, firstName: string, middleName: string, excludeId?: number): Observable<NameMatch[]> {
    let params = new HttpParams().set('surname', surname).set('firstName', firstName);
    if (middleName) {
      params = params.set('middleName', middleName);
    }
    if (excludeId !== undefined) {
      params = params.set('excludeId', excludeId);
    }
    return this.http.get<NameMatch[]>('/api/students/name-matches', { params });
  }

  pickLists(yearId?: number, studentId?: number): Observable<PickLists> {
    let params = new HttpParams();
    if (yearId !== undefined) {
      params = params.set('yearId', yearId);
    }
    if (studentId !== undefined) {
      params = params.set('studentId', studentId);
    }
    return this.http.get<PickLists>('/api/student-pick-lists', { params });
  }

  filterOptions(): Observable<FilterOptions> {
    return this.http.get<FilterOptions>('/api/student-filter-options');
  }

  putPhoto(id: number, file: File): Observable<{ photoVersion: number }> {
    const body = new FormData();
    body.append('file', file);
    return this.http.put<{ photoVersion: number }>(`/api/students/${id}/photo`, body);
  }

  deletePhoto(id: number): Observable<void> {
    return this.http.delete<void>(`/api/students/${id}/photo`);
  }

  photoUrl(id: number, version: number): string {
    return `/api/students/${id}/photo?v=${version}`;
  }
}
