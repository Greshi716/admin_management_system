import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { NEVER, of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { FeesService, StudentFees } from '../../fees/fees.service';
import { HistoryResult, Student, StudentsService } from '../students.service';
import { AuthStore } from '../../../core/auth/auth.store';
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
      feeLocked: false,
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
      feeLocked: false,
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

const FEES_SET: StudentFees = {
  yearId: 2,
  yearName: '2026-27',
  state: 'FEE_SET',
  yearFee: 28000,
  registrationFee: 500,
  totalFee: 28500,
  dueDate: '2026-07-15',
  broughtForward: { version: 1, amount: 2500, note: 'Test note' },
  totalDue: 31000,
  balance: {
    dueDate: '2026-07-15',
    yearFee: 28000,
    registrationFee: 500,
    broughtForward: 2500,
    totalFee: 31000,
    paid: 10500,
    pending: 20500,
    yearFeeDue: 18000,
    registrationDue: 0,
    earlierYearsPending: 2500,
    excessPaid: 0,
    paidUpTo: { state: 'DATE', date: '2026-07-09' },
  },
  receipts: [
    { serialNo: 7, receiptNo: 'R-7', receiptDate: '2026-06-01', receiptType: 'CURRENT_YEAR', paymentMode: 'CASH', total: 10500, status: 'VALID' },
    { serialNo: 8, receiptNo: 'R-8', receiptDate: '2026-06-02', receiptType: 'CURRENT_YEAR', paymentMode: 'CHEQUE', total: 500, status: 'CHEQUE_NOT_CLEARED' },
  ],
};

describe('StudentProfileComponent (FR-007, FR-009, FR-015, FR-019)', () => {
  let fixture: ComponentFixture<StudentProfileComponent>;
  let element: HTMLElement;
  let service: { get: ReturnType<typeof vi.fn>; history: ReturnType<typeof vi.fn>; markLeft: ReturnType<typeof vi.fn> };

  function create(
    history: HistoryResult = HISTORY,
    student: unknown = of(STUDENT),
    currentYear: string | null = null,
    fees: StudentFees = FEES_SET,
    feesResult: unknown = of(fees),
  ): void {
    service = {
      get: vi.fn().mockReturnValue(student),
      history: vi.fn().mockReturnValue(of(history)),
      markLeft: vi.fn(),
    };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: StudentsService, useValue: service },
        { provide: FeesService, useValue: { studentFees: vi.fn().mockReturnValue(feesResult) } },
        { provide: AuthStore, useValue: { currentYear: () => currentYear } },
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

  it('FR-007: the heading shows the record of the current year, even when a later year has one', () => {
    const next = { ...STUDENT.classHistory[0], academicYearId: 3, academicYearName: '2027-28', stdDiv: 'Standard 3-A' };
    create(HISTORY, of({ ...STUDENT, classHistory: [next, ...STUDENT.classHistory] }), '2026-27');

    expect(element.querySelector('[data-testid="heading-class"]')?.textContent).toContain('Standard 2-A · General · 2026-27');
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

  it('has no leaving certificate or roll number items', () => {
    create();
    const text = (element.textContent ?? '').toLowerCase();

    for (const word of ['leaving certificate', 'roll']) {
      expect(text).not.toContain(word);
    }
  });

  describe('Fees block with receipts (FR-012, FR-014)', () => {
    const text = (id: string) => element.querySelector(`[data-testid="${id}"]`)?.textContent?.trim();

    it('shows paid, pending, earlier years pending and Paid up to as the server sent them', () => {
      create();

      expect(text('fee-paid')).toBe('10,500.00');
      expect(text('fee-pending')).toBe('20,500.00');
      expect(text('fee-earlier-pending')).toBe('2,500.00');
      expect(element.querySelector('[data-testid="fee-excess"]')).toBeNull();
      expect(text('fee-paid-up-to')).toBe('9 Jul 2026');
    });

    it('says Fully paid for the year, Nothing paid and shows excess paid when there is one', () => {
      create(HISTORY, of(STUDENT), null, {
        ...FEES_SET,
        balance: { ...FEES_SET.balance, paidUpTo: { state: 'FULL', date: '2027-03-31' }, excessPaid: 400, pending: 0 },
      });
      expect(text('fee-paid-up-to')).toBe('Fully paid for the year');
      expect(text('fee-excess')).toBe('400.00');
    });

    it('says Nothing paid when nothing is paid', () => {
      create(HISTORY, of(STUDENT), null, { ...FEES_SET, balance: { ...FEES_SET.balance, paidUpTo: { state: 'NOTHING', date: null } } });
      expect(text('fee-paid-up-to')).toBe('Nothing paid');
    });

    it('lists the receipts with a link and a status tag, and has a Collect fee button for the student', () => {
      create();

      const links = Array.from(element.querySelectorAll('[data-testid="receipt-link"]')) as HTMLAnchorElement[];
      expect(links.map((a) => a.textContent?.trim())).toEqual(['R-7', 'R-8']);
      expect(links[0].getAttribute('href')).toBe('/finance/receipts/7');
      const tags = Array.from(element.querySelectorAll('[data-testid="receipt-status"]')).map((e) => e.textContent?.trim());
      expect(tags).toEqual(['Valid', 'Cheque not cleared']);
      expect(element.querySelector('[data-testid="collect-fee"]')?.getAttribute('href')).toContain('/finance/fee-collection');
    });

    it('says there are no receipts yet and offers no edit, cancel or delete control', () => {
      create(HISTORY, of(STUDENT), null, { ...FEES_SET, receipts: [] });

      expect(text('receipts-empty')).toBe('No receipts yet.');
      const buttons = (element.querySelector('[data-testid="fees-block"]')?.textContent ?? '').toLowerCase();
      for (const word of ['edit', 'cancel', 'delete']) {
        expect(buttons).not.toContain(word);
      }
    });
  });

  describe('Fees block (FR-005, FR-009, FR-011)', () => {
    const text = (id: string) => element.querySelector(`[data-testid="${id}"]`)?.textContent?.trim();

    it('shows the fee, registration fee, total, due date, brought forward with its note and the total due', () => {
      create();

      expect(text('fee-year-fee')).toBe('28,000.00');
      expect(text('fee-registration')).toBe('500.00');
      expect(text('fee-total')).toBe('28,500.00');
      expect(text('fee-due-date')).toBe('15-07-2026');
      expect(text('fee-brought-forward')).toContain('2,500.00');
      expect(text('fee-brought-forward-note')).toBe('Test note');
      expect(text('fee-total-due')).toBe('31,000.00');
    });

    it('leaves out the registration fee when there is none', () => {
      create(HISTORY, of(STUDENT), null, { ...FEES_SET, registrationFee: null, totalFee: 28000, totalDue: 30500 });

      expect(element.querySelector('[data-testid="fee-registration"]')).toBeNull();
      expect(text('fee-total')).toBe('28,000.00');
    });

    it('says Fee not set and still shows the brought-forward amount', () => {
      create(HISTORY, of(STUDENT), null, {
        ...FEES_SET,
        state: 'FEE_NOT_SET',
        yearFee: null,
        registrationFee: null,
        totalFee: null,
        dueDate: null,
        totalDue: 2500,
      });

      expect(text('fee-state')).toBe('Fee not set');
      expect(text('fee-brought-forward')).toContain('2,500.00');
      expect(text('fee-total-due')).toBe('2,500.00');
    });

    it('says Fee structure not finalised when the year has no accounts yet', () => {
      create(HISTORY, of(STUDENT), null, {
        ...FEES_SET,
        state: 'NOT_FINALISED',
        yearFee: null,
        registrationFee: null,
        totalFee: null,
        dueDate: null,
        broughtForward: { version: null, amount: 0, note: null },
        totalDue: 0,
      });

      expect(text('fee-state')).toBe('Fee structure not finalised');
      expect(text('fee-total-due')).toBe('0.00');
    });
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

  it('says the fees could not be loaded when the Fees block fails (FR-009)', () => {
    create(HISTORY, of(STUDENT), null, FEES_SET, throwError(() => new Error('down')));

    expect(element.querySelector('[data-testid="fees-error"]')?.textContent).toContain('could not be loaded');
    expect(element.querySelector('[data-testid="fee-total-due"]')).toBeNull();
  });

  it('shows a loading line until the Fees block arrives (FR-009)', () => {
    create(HISTORY, of(STUDENT), null, FEES_SET, NEVER);

    expect(element.querySelector('[data-testid="fees-loading"]')).not.toBeNull();
  });

  it('has a Change button on every class record row (FR-006)', () => {
    create();
    const rows = Array.from(element.querySelectorAll('[data-testid="class-history"] tbody tr'));

    expect(rows.every((row) => row.textContent?.includes('Change'))).toBe(true);
  });
});
