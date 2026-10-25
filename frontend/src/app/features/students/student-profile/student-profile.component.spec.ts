import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { HistoryResult, Student, StudentsService } from '../students.service';
import { StudentProfileComponent } from './student-profile.component';

const STUDENT: Student = {
  id: 5,
  version: 0,
  grNo: 'GR-5',
  srNo: 5,
  surname: 'Testsurname',
  firstName: 'Alpha',
  middleName: 'Middle',
  gender: 'Male',
  dob: '2012-05-10',
  birthPlace: null,
  fatherName: 'Test Father',
  motherName: null,
  mobile1: '9000000005',
  mobile2: '9000000006',
  address: 'Test address',
  aadhaar: '********9012',
  religionCaste: '*****caste',
  admissionDate: '2020-06-15',
  admissionStandardName: 'Standard 1',
  previousSchool: null,
  photoVersion: null,
  status: 'Active',
  source: 'Manual',
  classHistory: [
    {
      version: 0,
      academicYearId: 2,
      academicYearName: '2026-27',
      standardId: 1,
      divisionId: 1,
      stdDiv: 'Standard 2-A',
      categoryId: 1,
      categoryName: 'General',
      yearStatus: 'Studying',
    },
    {
      version: 0,
      academicYearId: 1,
      academicYearName: '2025-26',
      standardId: 1,
      divisionId: 1,
      stdDiv: 'Standard 1-A',
      categoryId: 1,
      categoryName: 'General',
      yearStatus: 'Promoted',
    },
  ],
};

const HISTORY: HistoryResult = {
  entries: [
    {
      at: '2026-10-25T10:00:00Z',
      userName: 'testadmin',
      action: 'Edit',
      record: 'Student#5',
      changes: { old: { address: 'Old test address' }, new: { address: 'Test address' } },
    },
    {
      at: '2026-10-24T10:00:00Z',
      userName: 'testadmin',
      action: 'Create',
      record: 'Student#5',
      changes: { new: { grNo: 'GR-5' } },
    },
  ],
  truncated: false,
};

describe('StudentProfileComponent (FR-007, FR-009, FR-015, FR-019)', () => {
  let fixture: ComponentFixture<StudentProfileComponent>;
  let element: HTMLElement;
  let service: { get: ReturnType<typeof vi.fn>; history: ReturnType<typeof vi.fn>; markLeft: ReturnType<typeof vi.fn> };

  function create(history: HistoryResult = HISTORY, student: unknown = of(STUDENT)): void {
    service = {
      get: vi.fn().mockReturnValue(student),
      history: vi.fn().mockReturnValue(of(history)),
      markLeft: vi.fn(),
    };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: StudentsService, useValue: service },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: '5' }) } } },
      ],
    });
    fixture = TestBed.createComponent(StudentProfileComponent);
    element = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  }

  it('loads the student and the history of the id in the address', () => {
    create();

    expect(service.get).toHaveBeenCalledWith(5);
    expect(service.history).toHaveBeenCalledWith(5);
  });

  it('shows the heading with name, status, GR No., class, category and year', () => {
    create();

    expect(element.querySelector('[data-testid="student-name"]')?.textContent).toBe('Alpha Middle Testsurname');
    expect(element.querySelector('[data-testid="status-badge"]')?.textContent).toBe('Active');
    expect(element.textContent).toContain('GR No. GR-5');
    expect(element.querySelector('[data-testid="heading-class"]')?.textContent).toContain(
      'Standard 2-A · General · 2026-27',
    );
  });

  it('shows the sections with full mobiles and masked Aadhaar and religion/caste', () => {
    create();
    const text = element.textContent ?? '';

    expect(text).toContain('Personal details');
    expect(text).toContain('Contact and admission');
    expect(text).toContain('9000000005');
    expect(text).toContain('9000000006');
    expect(element.querySelector('[data-testid="aadhaar"]')?.textContent).toBe('********9012');
    expect(element.querySelector('[data-testid="religion-caste"]')?.textContent).toBe('*****caste');
    expect(text).toContain('10-05-2012');
    expect(text).toContain('Manual');
  });

  it('lists the class history newest first', () => {
    create();
    const rows = Array.from(element.querySelectorAll('[data-testid="class-history"] tbody tr')).map(
      (row) => row.textContent,
    );

    expect(rows).toHaveLength(2);
    expect(rows[0]).toContain('2026-27');
    expect(rows[1]).toContain('Promoted');
  });

  it('shows the history with old and new values', () => {
    create();
    const text = element.querySelector('[data-testid="history"]')?.textContent ?? '';

    expect(text).toContain('Address: Old test address → Test address');
    expect(text).toContain('GR No.: GR-5');
    expect(element.querySelector('[data-testid="history-truncated"]')).toBeNull();
  });

  it('says older entries are in the Audit log when the history is cut', () => {
    create({ ...HISTORY, truncated: true });

    expect(element.querySelector('[data-testid="history-truncated"]')?.textContent).toContain(
      'Older entries are in the Audit log',
    );
  });

  it('has an Edit details button and a Back to students link', () => {
    create();

    const edit = Array.from(element.querySelectorAll('a')).find((a) => a.textContent?.trim() === 'Edit details');
    expect(edit?.getAttribute('href')).toBe('/students/5/edit');
    const back = Array.from(element.querySelectorAll('a')).find((a) => a.textContent?.includes('Back to students'));
    expect(back?.getAttribute('href')).toBe('/students');
  });

  it('has no fee, receipt, Collect fee, leaving certificate or roll number items', () => {
    create();
    const text = (element.textContent ?? '').toLowerCase();

    for (const word of ['fee', 'receipt', 'collect', 'leaving certificate', 'roll']) {
      expect(text).not.toContain(word);
    }
  });

  it('says so when the student cannot be loaded', () => {
    create(HISTORY, throwError(() => new Error('404')));

    expect(element.textContent).toContain('could not be loaded');
  });

  const button = (label: string) =>
    Array.from(element.querySelectorAll('button')).find((b) => b.textContent?.trim() === label);

  it('shows Mark as left for an Active student and not for a Left one (FR-004)', () => {
    create();
    expect(button('Mark as left')).toBeDefined();
  });

  it('hides Mark as left for a Left student', () => {
    create(HISTORY, of({ ...STUDENT, status: 'Left' }));
    expect(button('Mark as left')).toBeUndefined();
  });

  it('asks for a confirmation before marking as left and sends the loaded version', () => {
    create();
    service.markLeft.mockReturnValue(of({ ...STUDENT, status: 'Left' }));
    service.get.mockReturnValue(of({ ...STUDENT, status: 'Left', version: 1 }));

    button('Mark as left')?.click();
    fixture.detectChanges();
    expect(service.markLeft).not.toHaveBeenCalled();
    const confirm = Array.from(element.querySelectorAll('button')).filter((b) => b.textContent?.trim() === 'Mark as left')[1];
    confirm.click();
    fixture.detectChanges();

    expect(service.markLeft).toHaveBeenCalledWith(5, 0);
    expect(element.querySelector('[data-testid="status-badge"]')?.textContent).toBe('Left');
    expect(button('Mark as left')).toBeUndefined();
  });

  it('shows the stale-version dialog when the student was changed elsewhere (FR-018)', () => {
    create();
    service.markLeft.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409, error: { code: 'STALE_VERSION', current: { ...STUDENT, version: 3 } } })),
    );

    button('Mark as left')?.click();
    fixture.detectChanges();
    Array.from(element.querySelectorAll('button')).filter((b) => b.textContent?.trim() === 'Mark as left')[1].click();
    fixture.detectChanges();

    expect(element.querySelector('[data-testid="latest-values"]')).not.toBeNull();
  });

  it('has a Change button on every class record row (FR-006)', () => {
    create();
    const rows = Array.from(element.querySelectorAll('[data-testid="class-history"] tbody tr'));

    expect(rows.every((row) => row.textContent?.includes('Change'))).toBe(true);
  });
});
