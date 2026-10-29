import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Receipt } from '../receipts.service';
import { ReceiptMarkDialogComponent } from './receipt-mark-dialog.component';

const RECEIPT = { serialNo: 9, receiptNo: '3300', total: 5000, studentName: 'Test Student', balanceVersion: 'r1.m0.a1.b0' } as Receipt;

describe('ReceiptMarkDialogComponent (FR-013)', () => {
  let fixture: ComponentFixture<ReceiptMarkDialogComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ReceiptMarkDialogComponent);
    fixture.componentRef.setInput('receipt', RECEIPT);
    fixture.detectChanges();
  });

  const el = () => fixture.nativeElement as HTMLElement;
  const type = (value: string) => {
    const area = el().querySelector('#mark-reason') as HTMLTextAreaElement;
    area.value = value;
    area.dispatchEvent(new Event('input'));
  };
  const confirm = () => {
    (el().querySelector('[data-testid="mark-confirm"]') as HTMLButtonElement).click();
    fixture.detectChanges();
  };

  it('says what will happen: the amount is added back, it cannot be undone', () => {
    expect(el().querySelector('[data-testid="mark-explain"]')?.textContent).toContain('5,000.00 will be added back');
    expect(el().querySelector('[data-testid="mark-explain"]')?.textContent).toContain('cannot be undone');
  });

  it('sends the reason and the balance version and emits the marked receipt', () => {
    const marked = vi.fn();
    fixture.componentInstance.marked.subscribe(marked);
    type('Bounced');

    confirm();
    const request = http.expectOne('/api/receipts/9/not-cleared');
    expect(request.request.body).toEqual({ reason: 'Bounced', balanceVersion: 'r1.m0.a1.b0' });
    request.flush({ serialNo: 9, status: 'CHEQUE_NOT_CLEARED' });

    expect(marked).toHaveBeenCalledWith(expect.objectContaining({ status: 'CHEQUE_NOT_CLEARED' }));
  });

  it('shows an empty reason refused by the server beside the reason field', () => {
    confirm();
    http
      .expectOne('/api/receipts/9/not-cleared')
      .flush({ fieldErrors: [{ field: 'reason', message: 'Enter the reason the cheque did not clear.' }] }, { status: 422, statusText: 'Unprocessable' });
    fixture.detectChanges();

    expect(el().querySelector('[data-testid="mark-reason-error"]')?.textContent).toContain('Enter the reason');
  });

  it('hands a BALANCE_CHANGED answer to the parent and shows no message itself', () => {
    const changed = vi.fn();
    fixture.componentInstance.balanceChanged.subscribe(changed);
    type('Bounced');

    confirm();
    http
      .expectOne('/api/receipts/9/not-cleared')
      .flush({ code: 'BALANCE_CHANGED', current: { balanceVersion: 'r2' } }, { status: 409, statusText: 'Conflict' });

    expect(changed).toHaveBeenCalledTimes(1);
    expect(el().querySelector('[data-testid="mark-note"]')).toBeNull();
  });

  it('shows another refusal, such as ALREADY_MARKED, in a note', () => {
    type('Bounced');
    confirm();
    http
      .expectOne('/api/receipts/9/not-cleared')
      .flush({ code: 'ALREADY_MARKED', detail: 'This receipt is already marked.' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(el().querySelector('[data-testid="mark-note"]')?.textContent).toContain('already marked');
  });

  it('is closed while there is no receipt', () => {
    fixture.componentRef.setInput('receipt', null);
    fixture.detectChanges();

    expect(el().querySelector('#mark-reason')).toBeNull();
  });
});
