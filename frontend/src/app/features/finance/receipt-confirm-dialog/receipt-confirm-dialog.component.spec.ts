import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReceiptConfirmDetails, ReceiptConfirmDialogComponent } from './receipt-confirm-dialog.component';

const DETAILS: ReceiptConfirmDetails = {
  studentName: 'Test Student',
  grNo: 'T-1',
  stdDiv: '10 A',
  receiptNo: '3100',
  receiptDate: '2026-10-01',
  lines: [
    { label: 'Registration fee', amount: 500 },
    { label: 'Year fee', amount: 10000 },
  ],
  total: 10500,
  totalWords: 'Ten Thousand Five Hundred Rupees Only',
  mode: 'Cash',
  chequeNo: null,
  chequeDate: null,
  bankName: null,
};

describe('ReceiptConfirmDialogComponent (FR-008)', () => {
  let fixture: ComponentFixture<ReceiptConfirmDialogComponent>;
  let confirmed: ReturnType<typeof vi.fn<() => void>>;
  let cancelled: ReturnType<typeof vi.fn<() => void>>;

  function open(details: ReceiptConfirmDetails | null): void {
    fixture = TestBed.createComponent(ReceiptConfirmDialogComponent);
    fixture.componentRef.setInput('details', details);
    confirmed = vi.fn<() => void>();
    cancelled = vi.fn<() => void>();
    fixture.componentInstance.confirmed.subscribe(() => confirmed());
    fixture.componentInstance.cancelled.subscribe(() => cancelled());
    fixture.detectChanges();
  }

  const el = () => fixture.nativeElement as HTMLElement;
  const button = (label: string) => Array.from(el().querySelectorAll('button')).find((b) => b.textContent?.includes(label));

  it('shows the student, receipt no., date, each amount, the total and the words', () => {
    open(DETAILS);

    expect(el().querySelector('[data-testid="confirm-student"]')?.textContent).toContain('Test Student');
    expect(el().querySelector('[data-testid="confirm-receipt-no"]')?.textContent).toContain('3100');
    expect(el().querySelector('[data-testid="confirm-date"]')?.textContent).toContain('1 Oct 2026');
    const text = el().querySelector('[data-testid="confirm-details"]')?.textContent ?? '';
    expect(text).toContain('Registration fee');
    expect(text).toContain('500.00');
    expect(text).toContain('10,000.00');
    expect(el().querySelector('[data-testid="confirm-total"]')?.textContent).toContain('10,500.00');
    expect(el().querySelector('[data-testid="confirm-words"]')?.textContent).toContain('Ten Thousand Five Hundred Rupees Only');
  });

  it('shows the cheque details only for a cheque', () => {
    open({ ...DETAILS, mode: 'Cheque', chequeNo: '778899', chequeDate: '2026-10-01', bankName: 'Test Bank' });

    expect(el().textContent).toContain('778899');
    expect(el().textContent).toContain('Test Bank');
  });

  it('Cancel and Confirm each say so once, and nothing shows without details', () => {
    open(DETAILS);
    button('Cancel')?.click();
    expect(cancelled).toHaveBeenCalledTimes(1);
    expect(confirmed).not.toHaveBeenCalled();
    button('Confirm and save')?.click();
    expect(confirmed).toHaveBeenCalledTimes(1);

    open(null);
    expect(el().querySelector('[data-testid="confirm-details"]')).toBeNull();
  });
});
