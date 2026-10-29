import { ChangeDetectionStrategy, Component, inject, OnInit, signal, ViewEncapsulation } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { formatMoney } from '../../../shared/ui/money';
import { formatDate, receiptStatusText } from '../finance-format';
import { Receipt, ReceiptsService } from '../receipts.service';

/**
 * The printed receipt (FR-017, FR-018): one A4 page in English, a first draft of the layout of the school's receipt
 * book. Every print is the same page: the receipt is read with the ordinary GET, nothing is written when printing and
 * nothing on the page says it is a copy. There is no "Paid up to" line (FR-004). A receipt that does not count as paid
 * prints its status. The Print button only opens the browser's print dialog; the shell and the buttons are hidden in print.
 */
@Component({
  selector: 'app-receipt-print',
  imports: [RouterLink],
  encapsulation: ViewEncapsulation.None,
  styles: `
    @page {
      size: A4;
      margin: 15mm;
    }
    .receipt-sheet {
      max-width: 190mm;
      margin: 0 auto;
      background: #fff;
      color: #000;
      padding: 12mm;
      border: 1px solid #999;
    }
    .receipt-sheet table {
      width: 100%;
      border-collapse: collapse;
    }
    .receipt-sheet th,
    .receipt-sheet td {
      border: 1px solid #000;
      padding: 6px 10px;
      text-align: left;
    }
    .receipt-sheet td.amount,
    .receipt-sheet th.amount {
      text-align: right;
      font-variant-numeric: tabular-nums;
    }
    @media print {
      body:has(.receipt-sheet) > app-root header,
      body:has(.receipt-sheet) nav,
      body:has(.receipt-sheet) .no-print {
        display: none !important;
      }
      body:has(.receipt-sheet) main {
        padding: 0;
        max-width: none;
      }
      .receipt-sheet {
        border: none;
        padding: 0;
        max-width: none;
      }
    }
  `,
  template: `
    @if (receipt(); as r) {
      <div class="no-print mb-4 flex gap-2">
        <button type="button" class="btn btn-primary" (click)="print()" data-testid="print">Print</button>
        <a [routerLink]="['/finance/receipts', r.serialNo]" class="btn btn-secondary btn-outlined">Back to the receipt</a>
      </div>
      <article class="receipt-sheet" data-testid="receipt-sheet">
        <div class="mb-4 text-center">
          <h1 class="m-0 text-2xl font-bold" data-testid="print-school">{{ r.school.name }}</h1>
          @if (r.school.address) {
            <p class="m-0" data-testid="print-address">{{ r.school.address }}</p>
          }
          @if (r.school.phone) {
            <p class="m-0" data-testid="print-phone">{{ r.school.phone }}</p>
          }
          <p class="m-0 mt-2 text-lg font-bold">Fee receipt</p>
        </div>
        @if (r.status !== 'VALID') {
          <p class="mb-3 text-center text-lg font-bold" data-testid="print-status">{{ statusText(r.status) }}</p>
        }
        <div class="mb-3 flex justify-between">
          <span>Receipt no.: <strong data-testid="print-receipt-no">{{ r.receiptNo }}</strong></span>
          <span>Date: <strong data-testid="print-date">{{ date(r.receiptDate) }}</strong></span>
        </div>
        <p class="m-0">Student: <strong data-testid="print-student">{{ r.studentName }}</strong></p>
        <p class="m-0">GR No.: <strong data-testid="print-gr">{{ r.grNo }}</strong></p>
        <p class="mb-3 mt-0">Std / Div: <strong data-testid="print-class">{{ r.standardName }}@if (r.divisionName) { / {{ r.divisionName }} }</strong></p>
        <table class="mb-3" data-testid="print-rows">
          <thead>
            <tr><th>Particulars</th><th class="amount">Amount</th></tr>
          </thead>
          <tbody>
            @if (r.receiptType === 'CURRENT_YEAR') {
              <tr><td>Registration fee</td><td class="amount">{{ money(r.registrationPaid) }}</td></tr>
              <tr><td>Year fee ({{ r.academicYear }})</td><td class="amount">{{ money(r.feePaid) }}</td></tr>
            } @else {
              <tr><td>Previous years' fee ({{ r.academicYear }})</td><td class="amount">{{ money(r.feePaid) }}</td></tr>
            }
            <tr><th>Total</th><th class="amount" data-testid="print-total">{{ money(r.total) }}</th></tr>
          </tbody>
        </table>
        <p class="m-0">In words: <strong data-testid="print-words">{{ r.totalWords }}</strong></p>
        <p class="mb-0 mt-2" data-testid="print-mode">
          Mode: {{ r.paymentMode === 'CASH' ? 'Cash' : 'Cheque' }}
          @if (r.paymentMode === 'CHEQUE') {
            — Cheque no. {{ r.chequeNo }}, dated {{ date(r.chequeDate) }}, {{ r.bankName }}
          }
        </p>
        <div class="mt-16 flex justify-end">
          <div style="border-top: 1px solid #000; min-width: 60mm; text-align: center; padding-top: 4px" data-testid="print-signature">Signature</div>
        </div>
      </article>
    } @else if (failed()) {
      <p role="alert" class="rounded-control bg-error-bg px-4 py-3 text-error-text">This receipt could not be opened.</p>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReceiptPrintComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly receipts = inject(ReceiptsService);

  protected readonly receipt = signal<Receipt | null>(null);
  protected readonly failed = signal(false);

  protected readonly money = formatMoney;
  protected readonly date = formatDate;
  protected readonly statusText = receiptStatusText;

  ngOnInit(): void {
    this.receipts.get(Number(this.route.snapshot.paramMap.get('serialNo'))).subscribe({
      next: (receipt) => this.receipt.set(receipt),
      error: () => this.failed.set(true),
    });
  }

  /** Opens the browser's print dialog; nothing is sent to the server and nothing is recorded (FR-018). */
  protected print(): void {
    window.print();
  }
}
