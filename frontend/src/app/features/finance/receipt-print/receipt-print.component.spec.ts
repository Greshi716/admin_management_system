import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { ReceiptPrintComponent } from './receipt-print.component';

const BASE = {
  serialNo: 12,
  receiptNo: '3100',
  receiptDate: '2026-10-01',
  receiptType: 'CURRENT_YEAR',
  paymentMode: 'CASH',
  total: 10500,
  status: 'VALID',
  statusReason: null,
  studentId: 3,
  studentName: 'Test Student',
  grNo: 'T-1',
  standardName: 'Std 5',
  divisionName: 'A',
  academicYear: '2026-2027',
  registrationPaid: 500,
  feePaid: 10000,
  totalWords: 'Ten Thousand Five Hundred Rupees Only',
  chequeNo: null,
  chequeDate: null,
  bankName: null,
  school: { name: 'Test School', address: null, phone: null },
};

describe('ReceiptPrintComponent (FR-017, FR-018)', () => {
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

  function open(overrides: Record<string, unknown> = {}): HTMLElement {
    const fixture = TestBed.createComponent(ReceiptPrintComponent);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/receipts/12')
      .flush({ ...BASE, ...overrides });
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  /** The rows of the table as "particulars | amount" lines. */
  const rows = (el: HTMLElement) =>
    Array.from(el.querySelectorAll('[data-testid="print-rows"] tbody tr')).map((tr) =>
      Array.from(tr.querySelectorAll('td, th'))
        .map((cell) => cell.textContent?.trim())
        .join(' | '),
    );

  const text = (el: HTMLElement, id: string) => el.querySelector(`[data-testid="${id}"]`)?.textContent?.replace(/\s+/g, ' ').trim();

  it('prints the school, receipt no., date, student, GR No., Std / Div, both current-year rows, total, words and a signature line', () => {
    const el = open();

    expect(text(el, 'print-school')).toBe('Test School');
    expect(text(el, 'print-receipt-no')).toBe('3100');
    expect(text(el, 'print-date')).toBe('1 Oct 2026');
    expect(text(el, 'print-student')).toBe('Test Student');
    expect(text(el, 'print-gr')).toBe('T-1');
    expect(text(el, 'print-class')).toBe('Std 5 / A');
    expect(rows(el)).toEqual(['Registration fee | 500.00', 'Year fee (2026-2027) | 10,000.00', 'Total | 10,500.00']);
    expect(text(el, 'print-total')).toBe('10,500.00');
    expect(text(el, 'print-words')).toBe('Ten Thousand Five Hundred Rupees Only');
    expect(text(el, 'print-mode')).toBe('Mode: Cash');
    expect(el.querySelector('[data-testid="print-signature"]')).not.toBeNull();
  });

  it('leaves out the address and phone when the school has none and prints them when present', () => {
    expect(open().querySelector('[data-testid="print-address"]')).toBeNull();

    const el = open({ school: { name: 'Test School', address: '1 Test Road', phone: '0000 000000' } });
    expect(text(el, 'print-address')).toBe('1 Test Road');
    expect(text(el, 'print-phone')).toBe('0000 000000');
  });

  it('prints one previous-years row with the earlier year and no registration row', () => {
    const el = open({ receiptType: 'PREVIOUS_YEARS', academicYear: '2025-2026', registrationPaid: 0, feePaid: 2000, total: 2000 });

    expect(rows(el)).toEqual(["Previous years' fee (2025-2026) | 2,000.00", 'Total | 2,000.00']);
  });

  it('prints the cheque details for a Cheque receipt', () => {
    const el = open({ paymentMode: 'CHEQUE', chequeNo: '778899', chequeDate: '2026-09-30', bankName: 'Test Bank' });

    const mode = text(el, 'print-mode') ?? '';
    for (const part of ['Cheque', '778899', '30 Sep', '2026', 'Test Bank']) {
      expect(mode).toContain(part);
    }
  });

  it('prints the status of a receipt that does not count as paid, and none for a valid one', () => {
    expect(open().querySelector('[data-testid="print-status"]')).toBeNull();
    expect(text(open({ status: 'CHEQUE_NOT_CLEARED', statusReason: 'Bounced' }), 'print-status')).toBe('Cheque not cleared');
    expect(text(open({ status: 'VOIDED' }), 'print-status')).toBe('Voided by correction');
  });

  it('never says DUPLICATE, Paid up to or copy, for any status', () => {
    for (const status of ['VALID', 'CHEQUE_NOT_CLEARED', 'VOIDED']) {
      const content = (open({ status }).textContent ?? '').toLowerCase();
      expect(content).not.toContain('duplicate');
      expect(content).not.toContain('paid up to');
      expect(content).not.toContain('copy');
    }
  });

  it('Print opens the print dialog and sends nothing to the server', () => {
    const fixture = TestBed.createComponent(ReceiptPrintComponent);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/receipts/12').flush(BASE);
    fixture.detectChanges();
    const print = vi.spyOn(window, 'print').mockImplementation(() => undefined);

    (fixture.nativeElement.querySelector('[data-testid="print"]') as HTMLButtonElement).click();

    expect(print).toHaveBeenCalledTimes(1);
    http.verify();
  });
});
