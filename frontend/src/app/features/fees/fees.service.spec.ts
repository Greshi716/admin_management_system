import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { formatMoney } from '../../shared/ui/money';
import {
  feeConflictOf,
  FeeRowInput,
  FeesService,
  ReviseConfirmationRequired,
  StaleStructure,
} from './fees.service';

describe('FeesService (FR-001, FR-002, FR-004, FR-011, FR-013)', () => {
  let service: FeesService;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(FeesService);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  const input: FeeRowInput = {
    structureVersion: 3,
    standardId: 5,
    categoryId: null,
    amount: 36000.5,
    registrationFee: 500,
    dueDate: '2026-07-15',
  };

  it('reads the structure and the choices of a year', () => {
    service.structure(7).subscribe();
    controller.expectOne('/api/fee-structures/7').flush({});
    service.choices(7).subscribe();
    controller.expectOne('/api/fee-structures/7/choices').flush({});
  });

  it('adds a row with the amounts as numbers and no confirm by default', () => {
    service.addRow(7, input).subscribe();

    const request = controller.expectOne((r) => r.url === '/api/fee-structures/7/rows');
    expect(request.request.method).toBe('POST');
    expect(request.request.params.has('confirm')).toBe(false);
    expect(request.request.body).toEqual(input);
    expect(typeof request.request.body.amount).toBe('number');
    request.flush({});
  });

  it('sends confirm=true when the admin confirmed the revise warning', () => {
    service.editRow(7, 11, { ...input, version: 2 }, true).subscribe();

    const request = controller.expectOne((r) => r.url === '/api/fee-structures/7/rows/11');
    expect(request.request.method).toBe('PUT');
    expect(request.request.params.get('confirm')).toBe('true');
    expect(request.request.body.version).toBe(2);
    request.flush({});
  });

  it('deletes a row with the row version and the structure version as query parameters', () => {
    service.deleteRow(7, 11, 2, 3, true).subscribe();

    const request = controller.expectOne((r) => r.url === '/api/fee-structures/7/rows/11');
    expect(request.request.method).toBe('DELETE');
    expect(request.request.params.get('version')).toBe('2');
    expect(request.request.params.get('structureVersion')).toBe('3');
    expect(request.request.params.get('confirm')).toBe('true');
    request.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('finalises with the structure version', () => {
    service.finalise(7, 3).subscribe();

    const request = controller.expectOne('/api/fee-structures/7/finalise');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ version: 3 });
    request.flush({});
  });

  it('reads the fees of a student, for a year when one is given', () => {
    service.studentFees(4).subscribe();
    expect(controller.expectOne((r) => r.url === '/api/students/4/fees').request.params.has('yearId')).toBe(false);
    service.studentFees(4, 9).subscribe();
    const second = controller.expectOne((r) => r.url === '/api/students/4/fees' && r.params.has('yearId'));
    expect(second.request.params.get('yearId')).toBe('9');
    controller.match(() => true).forEach((r) => r.flush({}));
  });

  it('saves the brought-forward amount with PUT', () => {
    service.saveBroughtForward(4, { version: null, amount: 2500, note: 'Test note' }).subscribe();

    const request = controller.expectOne('/api/students/4/brought-forward');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ version: null, amount: 2500, note: 'Test note' });
    request.flush({});
  });

  describe('feeConflictOf', () => {
    const failure = (body: unknown) => new HttpErrorResponse({ status: 409, error: body });

    it('maps REVISE_CONFIRMATION_REQUIRED to the student count', () => {
      const conflict = feeConflictOf(failure({ code: 'REVISE_CONFIRMATION_REQUIRED', current: { studentCount: 5 } }));
      expect(conflict).toBeInstanceOf(ReviseConfirmationRequired);
      expect((conflict as ReviseConfirmationRequired).studentCount).toBe(5);
    });

    it('maps STALE_VERSION to the latest structure', () => {
      const latest = { yearId: 7, status: 'Finalised' };
      const conflict = feeConflictOf(failure({ code: 'STALE_VERSION', current: latest }));
      expect(conflict).toBeInstanceOf(StaleStructure);
      expect((conflict as StaleStructure).latest).toEqual(latest);
    });

    it('leaves every other error to the caller', () => {
      expect(feeConflictOf(failure({ code: 'SOMETHING_ELSE' }))).toBeNull();
      expect(feeConflictOf(new Error('x'))).toBeNull();
    });
  });
});

describe('formatMoney (FR-011)', () => {
  it('shows two decimals and Indian digit grouping', () => {
    expect(formatMoney(28000)).toBe('28,000.00');
    expect(formatMoney(28000.5)).toBe('28,000.50');
    expect(formatMoney(1234567.8)).toBe('12,34,567.80');
    expect(formatMoney(0)).toBe('0.00');
  });

  it('shows a dash for no value', () => {
    expect(formatMoney(null)).toBe('—');
    expect(formatMoney(undefined)).toBe('—');
  });
});
