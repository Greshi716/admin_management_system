import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { Balance, FeeCollection } from '../receipts.service';
import { ReceiptFormComponent } from './receipt-form.component';

const BALANCE: Balance = {
  dueDate: '2026-07-15',
  yearFee: 36500,
  registrationFee: 500,
  broughtForward: 0,
  totalFee: 37000,
  paid: 0,
  pending: 37000,
  yearFeeDue: 36500,
  registrationDue: 500,
  earlierYearsPending: 0,
  previousYearsPaid: 0,
  excessPaid: 0,
  paidUpTo: { state: 'NOTHING', date: null },
};

function context(overrides: Partial<FeeCollection> = {}): FeeCollection {
  return {
    student: { id: 7, name: 'Test Student', grNo: 'T-1', status: 'Active', stdDiv: '10 A' },
    currentYear: { id: 1, name: '2026-2027', startDate: '2026-04-01', endDate: '2027-03-31', state: 'FEE_SET' },
    canPayCurrentYear: true,
    canPayPreviousYears: false,
    chargesRegistrationFee: true,
    earlierYears: [],
    balance: BALANCE,
    balanceVersion: 'r0.m0.a1.b0',
    receipts: [],
    ...overrides,
  } as FeeCollection;
}

describe('ReceiptFormComponent (FR-001, FR-002, FR-003, FR-005, FR-007, FR-008, FR-016)', () => {
  let fixture: ComponentFixture<ReceiptFormComponent>;
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.fn>;

  function open(ctx: FeeCollection = context()): void {
    const params = new BehaviorSubject(convertToParamMap({ studentId: '7' }));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { queryParamMap: params, snapshot: {} } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(ReceiptFormComponent);
    fixture.detectChanges();
    http.expectOne('/api/students/7/fee-collection').flush(ctx);
    fixture.detectChanges();
  }

  const el = () => fixture.nativeElement as HTMLElement;
  const input = (id: string) => el().querySelector<HTMLInputElement | HTMLSelectElement>('#' + id)!;
  const text = (testId: string) => el().querySelector(`[data-testid="${testId}"]`)?.textContent ?? '';

  function type(id: string, value: string): void {
    const field = input(id);
    field.value = value;
    field.dispatchEvent(new Event(field instanceof HTMLSelectElement ? 'change' : 'input'));
    fixture.detectChanges();
  }

  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });

  it('shows the strip with the numbers the server sent, and the remaining due beside each row', () => {
    open();

    expect(text('student-name')).toContain('Test Student');
    expect(text('strip-net')).toContain('36,500.00');
    expect(text('strip-paid')).toContain('0.00');
    expect(text('strip-pending')).toContain('37,000.00');
    expect(text('strip-due')).toContain('15 Jul 2026');
    expect(text('registration-due')).toContain('500.00');
    expect(text('fee-due')).toContain('36,500.00');
    expect(el().textContent).not.toContain('Cancel receipt');
    expect(el().textContent).not.toContain('Need to cancel');
  });

  it('shows the cheque fields only for Cheque', () => {
    open();
    expect(el().querySelector('#cheque-no')).toBeNull();

    type('payment-mode', 'CHEQUE');
    expect(el().querySelector('#cheque-no')).not.toBeNull();
    expect(el().querySelector('#bank-name')).not.toBeNull();
    type('payment-mode', 'CASH');
    expect(el().querySelector('#cheque-no')).toBeNull();
  });

  it('hides the registration row for a standard that charges none', () => {
    open(context({ chargesRegistrationFee: false }));

    expect(el().querySelector('#registration-paid')).toBeNull();
    expect(el().querySelector('#fee-paid')).not.toBeNull();
  });

  it('gets the live total and the balance after from the server, not from the browser', () => {
    vi.useFakeTimers();
    open();

    type('registration-paid', '500');
    type('fee-paid', '10000.50');
    vi.advanceTimersByTime(300);
    const preview = http.expectOne('/api/students/7/fee-collection/preview');
    expect(preview.request.body.feePaid).toBe(10000.5);
    expect(preview.request.body.registrationPaid).toBe(500);
    preview.flush({
      registration: 500,
      fee: 10000.5,
      total: 10500.5,
      totalWords: 'Ten Thousand Five Hundred Rupees and Fifty Paise Only',
      balanceAfter: { ...BALANCE, pending: 26499.5, paidUpTo: { state: 'DATE', date: '2026-07-09' } },
    });
    fixture.detectChanges();

    expect(text('live-total')).toContain('10,500.50');
    expect(text('live-words')).toContain('Fifty Paise');
    expect(text('balance-after')).toContain('26,499.50');
    expect(text('balance-after')).toContain('9 Jul 2026');
  });

  it('Save opens the confirmation with the typed values; Cancel keeps them; Confirm saves and opens the receipt', () => {
    vi.useFakeTimers();
    open();
    type('receipt-no', '3100');
    type('fee-paid', '10000');
    vi.advanceTimersByTime(300);
    http.expectOne('/api/students/7/fee-collection/preview').flush({ registration: 0, fee: 10000, total: 10000, totalWords: 'Ten Thousand Rupees Only', balanceAfter: BALANCE });

    el().querySelector<HTMLButtonElement>('[data-testid="save"]')!.click();
    http.expectOne('/api/students/7/fee-collection/preview?validate=true').flush({ registration: 0, fee: 10000, total: 10000, totalWords: 'Ten Thousand Rupees Only', balanceAfter: BALANCE });
    fixture.detectChanges();
    expect(text('confirm-receipt-no')).toContain('3100');
    expect(text('confirm-total')).toContain('10,000.00');

    Array.from(el().querySelectorAll('button')).find((b) => b.textContent?.includes('Cancel'))!.click();
    fixture.detectChanges();
    expect(el().querySelector('[data-testid="confirm-details"]')).toBeNull();
    expect(input('receipt-no').value).toBe('3100');
    http.expectNone('/api/receipts');

    el().querySelector<HTMLButtonElement>('[data-testid="save"]')!.click();
    http.expectOne('/api/students/7/fee-collection/preview?validate=true').flush({ registration: 0, fee: 10000, total: 10000, totalWords: 'Ten Thousand Rupees Only', balanceAfter: BALANCE });
    fixture.detectChanges();
    Array.from(el().querySelectorAll('button')).find((b) => b.textContent?.includes('Confirm and save'))!.click();
    const create = http.expectOne('/api/receipts');
    expect(create.request.body.receiptNo).toBe('3100');
    expect(create.request.body.feePaid).toBe(10000);
    expect(create.request.body.balanceVersion).toBe('r0.m0.a1.b0');
    create.flush({ serialNo: 41, receiptNo: '3100' }, { status: 201, statusText: 'Created' });

    expect(navigate).toHaveBeenCalledWith(['/finance/receipts', 41]);
  });

  it('shows an overpayment refusal beside the year fee with the amount still due', () => {
    vi.useFakeTimers();
    open();
    type('fee-paid', '30000');
    vi.advanceTimersByTime(300);

    http.expectOne('/api/students/7/fee-collection/preview').flush(
      {
        status: 409,
        code: 'OVERPAYMENT',
        detail: 'Only 26500.00 is still due for the year fee.',
        fieldErrors: [{ field: 'feePaid', message: 'Only 26500.00 is still due for the year fee.' }],
        current: { due: 26500 },
      },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();

    expect(el().textContent).toContain('Only 26500.00 is still due for the year fee.');
    expect(el().querySelector('[data-testid="live-total"]')).toBeNull();
  });

  it('shows the REGISTRATION_PART_PAYMENT message with the amount due beside the field and in the note (FR-027)', () => {
    vi.useFakeTimers();
    open();
    type('registration-paid', '200');
    vi.advanceTimersByTime(300);

    http.expectOne('/api/students/7/fee-collection/preview').flush(
      {
        status: 409,
        code: 'REGISTRATION_PART_PAYMENT',
        detail: 'The registration fee of 500.00 must be paid in full.',
        fieldErrors: [{ field: 'registrationPaid', message: 'The registration fee of 500.00 must be paid in full.' }],
        current: { amountDue: 500 },
      },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();

    expect(el().textContent).toContain('The registration fee of 500.00 must be paid in full.');
    expect(el().querySelector('[data-testid="form-note"]')?.textContent).toContain(
      'The registration fee of 500.00 must be paid in full.',
    );
  });

  it('shows the brought-forward badge in the strip and banner when brought-forward amount is positive (FR-020, FR-023)', () => {
    open(
      context({
        canPayCurrentYear: false,
        canPayPreviousYears: true,
        earlierYears: [{ id: 99, name: '2025-26' }],
        balance: { ...BALANCE, broughtForward: 3000, earlierYearsPending: 3000 },
      }),
    );
    fixture.detectChanges();

    const badges = Array.from(el().querySelectorAll('[data-testid="brought-forward-badge"]'));
    expect(badges.length).toBeGreaterThanOrEqual(1);
    expect(badges[0].textContent).toContain('brought forward');
    expect(el().querySelector('[data-testid="strip-brought-forward-amount"]')?.textContent).toContain('3,000.00');
  });

  it('a refused number or date shows beside its field after Confirm, and nothing is lost', () => {
    vi.useFakeTimers();
    open();
    type('receipt-no', '3100');
    type('fee-paid', '100');
    vi.advanceTimersByTime(300);
    http.expectOne('/api/students/7/fee-collection/preview').flush({ registration: 0, fee: 100, total: 100, totalWords: 'x', balanceAfter: BALANCE });
    el().querySelector<HTMLButtonElement>('[data-testid="save"]')!.click();
    http.expectOne('/api/students/7/fee-collection/preview?validate=true').flush({ registration: 0, fee: 100, total: 100, totalWords: 'x', balanceAfter: BALANCE });
    fixture.detectChanges();
    Array.from(el().querySelectorAll('button')).find((b) => b.textContent?.includes('Confirm and save'))!.click();

    http.expectOne('/api/receipts').flush(
      {
        status: 409,
        code: 'DUPLICATE_RECEIPT_NO',
        detail: 'Receipt no. 3100 was already used on 1 Oct 2026 (serial no. 4).',
        fieldErrors: [{ field: 'receiptNo', message: 'Receipt no. 3100 was already used on 1 Oct 2026 (serial no. 4).' }],
      },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();

    expect(el().textContent).toContain('already used on 1 Oct 2026');
    expect(el().querySelector('[data-testid="confirm-details"]')).toBeNull();
    expect(input('receipt-no').value).toBe('3100');
    expect(input('fee-paid').value).toBe('100');
  });

  it('BALANCE_CHANGED opens the pop-up; Refresh takes the new balance and keeps what was typed', () => {
    vi.useFakeTimers();
    open();
    type('receipt-no', '3100');
    type('fee-paid', '100');
    vi.advanceTimersByTime(300);
    http.expectOne('/api/students/7/fee-collection/preview').flush({ registration: 0, fee: 100, total: 100, totalWords: 'x', balanceAfter: BALANCE });
    el().querySelector<HTMLButtonElement>('[data-testid="save"]')!.click();
    http.expectOne('/api/students/7/fee-collection/preview?validate=true').flush({ registration: 0, fee: 100, total: 100, totalWords: 'x', balanceAfter: BALANCE });
    fixture.detectChanges();
    Array.from(el().querySelectorAll('button')).find((b) => b.textContent?.includes('Confirm and save'))!.click();

    const fresh = context({ balanceVersion: 'r1.m0.a1.b0', balance: { ...BALANCE, paid: 500, pending: 36500 } });
    http.expectOne('/api/receipts').flush(
      { status: 409, code: 'BALANCE_CHANGED', detail: 'The balance has changed.', current: fresh },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();
    expect(text('balance-changed-text')).toContain('Nothing was saved');

    Array.from(el().querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Refresh')!.click();
    vi.advanceTimersByTime(300);
    fixture.detectChanges();
    http.expectOne('/api/students/7/fee-collection/preview').flush({ registration: 0, fee: 100, total: 100, totalWords: 'x', balanceAfter: BALANCE });

    expect(el().querySelector('[data-testid="balance-changed-text"]')).toBeNull();
    expect(text('strip-paid')).toContain('500.00');
    expect(input('receipt-no').value).toBe('3100');
    expect(input('fee-paid').value).toBe('100');
  });

  it('explains why a current-year receipt is not possible and offers the previous-years type first when it is open', () => {
    open(
      context({
        canPayCurrentYear: false,
        canPayPreviousYears: true,
        earlierYears: [{ id: 5, name: '2025-2026' }],
        balance: { ...BALANCE, earlierYearsPending: 3000 },
      }),
    );

    expect(text('earlier-first')).toContain('3,000.00');
    expect((input('receipt-type') as HTMLSelectElement).value).toBe('PREVIOUS_YEARS');
    expect(el().querySelector('#previous-year')).not.toBeNull();
    expect(el().querySelector('#registration-paid')).toBeNull();
  });

  it('shows the order message with a link that switches to the previous-years type, and one amount row without registration', () => {
    open(
      context({
        canPayCurrentYear: false,
        canPayPreviousYears: true,
        earlierYears: [{ id: 5, name: '2025-2026' }],
        balance: { ...BALANCE, earlierYearsPending: 3000 },
      }),
    );
    const select = input('receipt-type') as HTMLSelectElement;
    select.value = 'CURRENT_YEAR';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    (el().querySelector('[data-testid="use-previous-years"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect((input('receipt-type') as HTMLSelectElement).value).toBe('PREVIOUS_YEARS');
    expect(text('fee-due')).toContain('3,000.00');
    expect(el().querySelector('#registration-paid')).toBeNull();
    expect(el().querySelectorAll('[id="fee-paid"]').length).toBe(1);
  });

  it('offers the previous-years type only when earlier years have a pending amount', () => {
    open(context());

    const options = Array.from((input('receipt-type') as HTMLSelectElement).options).map((o) => o.value);
    expect(options).toEqual(['CURRENT_YEAR']);
  });

  it('says "Fee not set" and gives no form when the fee is not set', () => {
    open(
      context({
        canPayCurrentYear: false,
        currentYear: { id: 1, name: '2026-2027', startDate: '2026-04-01', endDate: '2027-03-31', state: 'FEE_NOT_SET' },
      }),
    );

    expect(text('fee-state')).toContain("Fee not set for this student's standard and category.");
    expect(el().querySelector('form')).toBeNull();
  });
});
