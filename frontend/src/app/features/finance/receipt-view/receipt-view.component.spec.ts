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
