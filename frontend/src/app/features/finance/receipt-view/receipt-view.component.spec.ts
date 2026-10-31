import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { ReceiptViewComponent } from './receipt-view.component';

describe('ReceiptViewComponent (FR-010, FR-017)', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ serialNo: '12' }) } } },
      ],
    }),
  );

  it('shows the stored values and the status, and offers no edit, cancel or delete', () => {
    const fixture = TestBed.createComponent(ReceiptViewComponent);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/receipts/12')
      .flush({
        serialNo: 12,
        receiptNo: '3100',
        receiptDate: '2026-10-01',
        receiptType: 'CURRENT_YEAR',
        paymentMode: 'CASH',
        total: 10500,
        status: 'VOIDED',
        statusReason: 'Typed twice',
        studentId: 3,
        studentName: 'Test Student',
        grNo: 'T-1',
        standardName: '5',
        divisionName: 'A',
        academicYear: '2026-2027',
        registrationPaid: 500,
        feePaid: 10000,
        totalWords: 'Rupees Ten Thousand Five Hundred Only',
      });
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="receipt-status"]')?.textContent).toContain('Voided by correction');
    expect(el.querySelector('[data-testid="receipt-status"]')?.textContent).toContain('Typed twice');
    expect(el.querySelector('[data-testid="receipt-total"]')?.textContent).toContain('10,500.00');
    expect(el.querySelectorAll('button').length).toBe(0);
  });

  function open(overrides: Record<string, unknown>): { fixture: ReturnType<typeof TestBed.createComponent<ReceiptViewComponent>>; el: HTMLElement } {
    const fixture = TestBed.createComponent(ReceiptViewComponent);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/receipts/12')
      .flush({
        serialNo: 12,
        receiptNo: '3300',
        receiptDate: '2026-10-01',
        receiptType: 'CURRENT_YEAR',
        paymentMode: 'CHEQUE',
        chequeNo: '778899',
        bankName: 'Test Bank',
        chequeDate: '2026-10-01',
        total: 5000,
        status: 'VALID',
        statusReason: null,
        studentId: 3,
        studentName: 'Test Student',
        grNo: 'T-1',
        academicYear: '2026-2027',
        registrationPaid: 0,
        feePaid: 5000,
        totalWords: 'Five Thousand Rupees Only',
        balanceVersion: 'r1',
        ...overrides,
      });
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('offers Cheque not cleared only for a valid Cheque receipt, and no edit, cancel or delete', () => {
    const { el } = open({});
    expect(el.querySelector('[data-testid="mark-not-cleared"]')).not.toBeNull();
    const words = Array.from(el.querySelectorAll('button')).map((b) => (b.textContent ?? '').toLowerCase()).join(' ');
    for (const word of ['edit', 'cancel receipt', 'delete']) {
      expect(words).not.toContain(word);
    }

    expect(open({ paymentMode: 'CASH' }).el.querySelector('[data-testid="mark-not-cleared"]')).toBeNull();
    expect(open({ status: 'CHEQUE_NOT_CLEARED', statusReason: 'Bounced' }).el.querySelector('[data-testid="mark-not-cleared"]')).toBeNull();
    expect(open({ status: 'VOIDED' }).el.querySelector('[data-testid="mark-not-cleared"]')).toBeNull();
  });

  it('shows the balance effect of a cheque that did not clear', () => {
    const { el } = open({ status: 'CHEQUE_NOT_CLEARED', statusReason: 'Bounced' });

    expect(el.querySelector('[data-testid="balance-effect"]')?.textContent).toContain('5,000.00 was added back');
    expect(el.querySelector('[data-testid="receipt-status-tag"]')?.textContent).toContain('Cheque not cleared');
  });

  it('shows one previous-years row and the earlier year for a previous-years receipt', () => {
    const { el } = open({ receiptType: 'PREVIOUS_YEARS', academicYear: '2025-2026', paymentMode: 'CASH' });

    expect(el.textContent).toContain("Previous years' fee");
    expect(el.textContent).toContain('2025-2026');
    expect(el.textContent).not.toContain('Registration fee');
  });

  it('says so when the receipt cannot be opened', () => {
    const fixture = TestBed.createComponent(ReceiptViewComponent);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/receipts/12')
      .flush({}, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('could not be opened');
  });
});
