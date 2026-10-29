import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { FilterOptions } from '../../students/students.service';
import { registerColumns, registerStatusClass } from './receipt-columns';
import { ReceiptListComponent } from './receipt-list.component';

const OPTIONS: FilterOptions = {
  standards: [{ id: 7, name: 'Std 7', displayOrder: 7, stream: null, active: true }],
  divisions: [],
  categories: [],
};

const ROWS = {
  rows: [
    {
      serialNo: 12,
      studentId: 3,
      receiptNo: '3100',
      receiptDate: '2026-10-01',
      student: 'Test Student',
      grNo: 'T-1',
      stdDiv: 'Std 7-A',
      type: 'Current year',
      mode: 'Cash',
      registrationPaid: 500,
      yearFee: 10000,
      previousYearsFee: 0,
      total: 10500,
      status: 'Valid',
    },
    {
      serialNo: 13,
      studentId: 3,
      receiptNo: '3101',
      receiptDate: '2026-10-02',
      student: 'Test Student',
      grNo: 'T-1',
      stdDiv: 'Std 7-A',
      type: 'Current year',
      mode: 'Cheque',
      registrationPaid: 0,
      yearFee: 3000,
      previousYearsFee: 0,
      total: 3000,
      status: 'Cheque not cleared',
    },
  ],
  total: 2,
};

const DAYS = [{ date: '2026-10-01', count: 1, cash: 10500, cheque: 0, total: 10500 }];

describe('ReceiptListComponent (FR-019, FR-020)', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] }));

  function open(): { el: HTMLElement; http: HttpTestingController; detect: () => void } {
    const fixture = TestBed.createComponent(ReceiptListComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne('/api/student-filter-options').flush(OPTIONS);
    fixture.detectChanges();
    http.expectOne('/api/receipts/search').flush(ROWS);
    fixture.detectChanges();
    return { el: fixture.nativeElement as HTMLElement, http, detect: () => fixture.detectChanges() };
  }

  const text = (el: HTMLElement, id: string) => Array.from(el.querySelectorAll(`[data-testid="${id}"]`)).map((n) => n.textContent?.replace(/\s+/g, ' ').trim());

  it('lists the receipts with amounts to two decimals, status tags and a link to each receipt', () => {
    const { el } = open();

    expect(text(el, 'amount-total')).toEqual(['10,500.00', '3,000.00']);
    expect(text(el, 'amount-registrationPaid')).toEqual(['500.00', '0.00']);
    expect(text(el, 'receipt-status')).toEqual(['Valid', 'Cheque not cleared']);
    expect(Array.from(el.querySelectorAll('[data-testid="open-receipt"]')).map((a) => a.getAttribute('href'))).toEqual([
      '/finance/receipts/12',
      '/finance/receipts/13',
    ]);
  });

  it('asks the server for the day-wise totals of the same filters and shows them as sent', () => {
    const { el, http, detect } = open();

    const request = http.expectOne('/api/receipts/day-totals');
    expect(request.request.body).toEqual({ first: 0, rows: 10, filters: {} });
    request.flush(DAYS);
    detect();

    expect(text(el, 'day-count')).toEqual(['1']);
    expect(text(el, 'day-cash')).toEqual(['10,500.00']);
    expect(text(el, 'day-cheque')).toEqual(['0.00']);
    expect(text(el, 'day-total')).toEqual(['10,500.00']);
    expect(el.querySelector('[data-testid="day-totals-empty"]')).toBeNull();
  });

  it('says so when the filters match no valid receipt', () => {
    const { el, http, detect } = open();

    http.expectOne('/api/receipts/day-totals').flush([]);
    detect();

    expect(text(el, 'day-totals-empty').length).toBe(1);
  });

  it('shows no Paid up to column and the three cheque columns only in the export', () => {
    const headers = registerColumns(OPTIONS)
      .filter((column) => !column.hidden)
      .map((column) => column.header);

    expect(headers).toEqual([
      'Receipt no.',
      'Date',
      'Student',
      'GR No.',
      'Std / Div',
      'Type',
      'Mode',
      'Registration fee',
      'Year fee',
      "Previous years' fee",
      'Total',
      'Status',
    ]);
    expect(registerColumns(OPTIONS).filter((column) => column.hidden).map((column) => column.field)).toEqual([
      'serialNo',
      'studentId',
      'chequeNo',
      'chequeDate',
      'bankName',
    ]);
  });

  it('offers the standards of the filter options and the status and mode words of the server', () => {
    const columns = registerColumns(OPTIONS);
    const dropdown = (field: string) => columns.find((column) => column.field === field)?.dropdowns?.[0];

    expect(dropdown('stdDiv')?.options).toEqual([{ value: 7, label: 'Std 7' }]);
    expect(dropdown('stdDiv')?.field).toBe('standardId');
    expect(dropdown('mode')?.options.map((o) => o.value)).toEqual(['Cash', 'Cheque']);
    expect(dropdown('status')?.options.map((o) => o.value)).toEqual(['Valid', 'Cheque not cleared', 'Voided by correction']);
    expect(columns.find((column) => column.field === 'receiptDate')?.type).toBe('date');
  });

  it('tags each status in its own colour', () => {
    expect(registerStatusClass('Valid')).toContain('violet');
    expect(registerStatusClass('Cheque not cleared')).toContain('warning');
    expect(registerStatusClass('Voided by correction')).toContain('error');
    expect(registerStatusClass('anything else')).toBe('');
  });
});
