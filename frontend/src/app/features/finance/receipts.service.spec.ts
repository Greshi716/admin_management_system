import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  BalanceChanged,
  BroughtForwardPending,
  DuplicateReceiptNo,
  Overpayment,
  ReceiptInput,
  ReceiptRefusal,
  receiptConflictOf,
  ReceiptsService,
} from './receipts.service';

function conflict(body: object): HttpErrorResponse {
  return new HttpErrorResponse({ status: 409, error: body });
}

describe('ReceiptsService (FR-005, FR-016, FR-019)', () => {
  let service: ReceiptsService;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(ReceiptsService);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  const input: ReceiptInput = {
    studentId: 7,
    receiptType: 'CURRENT_YEAR',
    receiptNo: '3100',
    receiptDate: '2026-10-01',
    registrationPaid: 0,
    feePaid: 1234.5,
    paymentMode: 'CASH',
    balanceVersion: 'r1.m0.a0.b0',
  };

  it('reads the fee collection context and sends the preview and the receipt as typed, amounts unchanged', () => {
    service.feeCollection(7).subscribe();
    expect(controller.expectOne('/api/students/7/fee-collection').request.method).toBe('GET');

    service.preview(7, input).subscribe();
    const preview = controller.expectOne('/api/students/7/fee-collection/preview');
    expect(preview.request.method).toBe('POST');
    expect(preview.request.body.feePaid).toBe(1234.5);

    service.preview(7, input, true).subscribe();
    expect(controller.expectOne('/api/students/7/fee-collection/preview?validate=true').request.method).toBe('POST');

    service.create(input).subscribe();
    const create = controller.expectOne('/api/receipts');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual(input);
  });

  it('reads one receipt, marks a cheque and loads the register, the day totals and the export', () => {
    service.get(12).subscribe();
    expect(controller.expectOne('/api/receipts/12').request.method).toBe('GET');

    service.markNotCleared(12, 'Bounced', 'v1').subscribe();
    const mark = controller.expectOne('/api/receipts/12/not-cleared');
    expect(mark.request.body).toEqual({ reason: 'Bounced', balanceVersion: 'v1' });

    const table = { first: 0, rows: 20, filters: {} };
    service.search(table).subscribe();
    expect(controller.expectOne('/api/receipts/search').request.body).toEqual(table);
    service.dayTotals(table).subscribe();
    expect(controller.expectOne('/api/receipts/day-totals').request.body).toEqual(table);
    service.exportRegister(table).subscribe();
    const exported = controller.expectOne('/api/receipts/export');
    expect(exported.request.responseType).toBe('blob');
  });

  it('turns BALANCE_CHANGED into a typed error carrying the fresh context', () => {
    const fresh = { balanceVersion: 'r2.m0.a0.b0' };
    const typed = receiptConflictOf(conflict({ code: 'BALANCE_CHANGED', current: fresh }));
    expect(typed).toBeInstanceOf(BalanceChanged);
    expect((typed as BalanceChanged).current).toEqual(fresh);
  });

  it('turns OVERPAYMENT into a typed error with the field and the amount still due', () => {
    const typed = receiptConflictOf(
      conflict({
        code: 'OVERPAYMENT',
        detail: 'Only 26500.00 is still due for the year fee.',
        fieldErrors: [{ field: 'feePaid', message: 'x' }],
        current: { due: 26500 },
      }),
    );
    expect(typed).toBeInstanceOf(Overpayment);
    expect((typed as Overpayment).field).toBe('feePaid');
    expect((typed as Overpayment).due).toBe(26500);
    expect((typed as Overpayment).message).toContain('26500.00');
  });

  it('turns BROUGHT_FORWARD_PENDING and DUPLICATE_RECEIPT_NO into typed errors', () => {
    const pending = receiptConflictOf(conflict({ code: 'BROUGHT_FORWARD_PENDING', current: { pending: 3000 } }));
    expect(pending).toBeInstanceOf(BroughtForwardPending);
    expect((pending as BroughtForwardPending).pending).toBe(3000);

    const duplicate = receiptConflictOf(conflict({ code: 'DUPLICATE_RECEIPT_NO', detail: 'Used on 2026-10-01 (no. 4).' }));
    expect(duplicate).toBeInstanceOf(DuplicateReceiptNo);
    expect((duplicate as DuplicateReceiptNo).message).toBe('Used on 2026-10-01 (no. 4).');
  });

  it('treats a code-less 409 on receiptNo as a duplicate number (two saves at once)', () => {
    const typed = receiptConflictOf(
      conflict({ detail: 'This receipt number is already used by another receipt.', fieldErrors: [{ field: 'receiptNo', message: 'x' }] }),
    );
    expect(typed).toBeInstanceOf(DuplicateReceiptNo);
  });

  it('keeps other coded refusals with their code and leaves 422 and network errors alone', () => {
    const other = receiptConflictOf(conflict({ code: 'FEE_NOT_SET', detail: 'Fee not set.' }));
    expect(other).toBeInstanceOf(ReceiptRefusal);
    expect((other as ReceiptRefusal).code).toBe('FEE_NOT_SET');
    expect(receiptConflictOf(new HttpErrorResponse({ status: 422, error: { fieldErrors: [] } }))).toBeNull();
    expect(receiptConflictOf(new Error('offline'))).toBeNull();
  });
});
