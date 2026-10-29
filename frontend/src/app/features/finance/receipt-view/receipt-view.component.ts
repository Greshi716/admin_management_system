import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { formatMoney } from '../../../shared/ui/money';
import { PageSkeletonComponent } from '../../../shared/ui/page-skeleton.component';
import { BalanceChangedDialogComponent } from '../balance-changed-dialog/balance-changed-dialog.component';
import { formatDate, receiptStatusClass, receiptStatusText } from '../finance-format';
import { ReceiptMarkDialogComponent } from '../receipt-mark-dialog/receipt-mark-dialog.component';
import { BalanceChanged, FeeCollection, Receipt, ReceiptsService } from '../receipts.service';

/**
 * One saved receipt exactly as stored, with its status (FR-010, FR-017) and, for an unmarked Cheque receipt, the one
 * action Cheque not cleared (FR-013). The receipt has no edit, cancel or delete action (FR-009). Printing is added by
 * the print view of a later task.
 */
@Component({
  selector: 'app-receipt-view',
  imports: [RouterLink, PageSkeletonComponent, ReceiptMarkDialogComponent, BalanceChangedDialogComponent],
  template: `
    @if (loading()) {
      <app-page-skeleton />
    } @else if (failed()) {
      <p role="alert" class="rounded-control bg-error-bg px-4 py-3 text-error-text">This receipt could not be opened.</p>
    } @else if (receipt(); as r) {
      <section class="rounded-card border border-border bg-surface p-6" aria-labelledby="receipt-heading">
        <h1 id="receipt-heading" class="mb-1 text-2xl font-bold">Receipt {{ r.receiptNo }}</h1>
        <p class="mb-4 text-muted" data-testid="receipt-status">
          <span class="rounded-full px-2 py-0.5 text-xs font-semibold" [class]="statusClass(r.status)" data-testid="receipt-status-tag">{{ statusText(r.status) }}</span>
          @if (r.statusReason) {
            <span data-testid="receipt-status-reason"> — {{ r.statusReason }}</span>
          }
        </p>
        @if (r.status === 'CHEQUE_NOT_CLEARED') {
          <p class="mb-4 rounded-card bg-warning-bg p-3 text-warning-text" data-testid="balance-effect">
            This cheque did not clear: {{ money(r.total) }} was added back to the balance and is not counted as paid.
          </p>
        } @else if (r.status === 'VOIDED') {
          <p class="mb-4 rounded-card bg-error-bg p-3 text-error-text" data-testid="balance-effect">
            This receipt was voided by a correction and is not counted as paid.
          </p>
        }
        <dl class="grid gap-x-8 gap-y-2 sm:grid-cols-2">
          <div><dt class="text-sm text-muted">Serial no.</dt><dd data-testid="serial-no">{{ r.serialNo }}</dd></div>
          <div><dt class="text-sm text-muted">Date</dt><dd>{{ date(r.receiptDate) }}</dd></div>
          <div>
            <dt class="text-sm text-muted">Student</dt>
            <dd><a [routerLink]="['/students', r.studentId]">{{ r.studentName }}</a> (GR {{ r.grNo }})</dd>
          </div>
          <div><dt class="text-sm text-muted">Class</dt><dd>{{ r.standardName }} {{ r.divisionName }}</dd></div>
          <div>
            <dt class="text-sm text-muted">{{ r.receiptType === 'PREVIOUS_YEARS' ? 'Earlier academic year' : 'Academic year' }}</dt>
            <dd>{{ r.academicYear }}</dd>
          </div>
          <div>
            <dt class="text-sm text-muted">Mode</dt>
            <dd>
              {{ r.paymentMode === 'CASH' ? 'Cash' : 'Cheque' }}
              @if (r.paymentMode === 'CHEQUE') { — {{ r.chequeNo }}, {{ r.bankName }}, {{ date(r.chequeDate) }} }
            </dd>
          </div>
          @if (r.receiptType === 'CURRENT_YEAR') {
            <div><dt class="text-sm text-muted">Registration fee</dt><dd>{{ money(r.registrationPaid) }}</dd></div>
            <div><dt class="text-sm text-muted">Year fee</dt><dd>{{ money(r.feePaid) }}</dd></div>
          } @else {
            <div><dt class="text-sm text-muted">Previous years' fee</dt><dd>{{ money(r.feePaid) }}</dd></div>
          }
          <div><dt class="text-sm text-muted">Total</dt><dd class="font-semibold" data-testid="receipt-total">{{ money(r.total) }}</dd></div>
          <div><dt class="text-sm text-muted">In words</dt><dd>{{ r.totalWords }}</dd></div>
        </dl>
        <div class="mt-5 flex flex-wrap gap-2">
          <a [routerLink]="['/finance/receipts', r.serialNo, 'print']" class="btn btn-primary" data-testid="print-receipt">Print</a>
          @if (r.status === 'VALID' && r.paymentMode === 'CHEQUE') {
            <button type="button" class="btn btn-danger btn-outlined" (click)="marking.set(true)" data-testid="mark-not-cleared">Cheque not cleared</button>
          }
        </div>
      </section>
      <app-receipt-mark-dialog
        [receipt]="marking() ? r : null"
        (marked)="marked($event)"
        (cancelled)="marking.set(false)"
        (balanceChanged)="staleBalance($event)"
      />
      <app-balance-changed-dialog [latest]="stale()" (refresh)="refresh()" />
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReceiptViewComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly receipts = inject(ReceiptsService);

  protected readonly loading = signal(true);
  protected readonly failed = signal(false);
  protected readonly receipt = signal<Receipt | null>(null);
  protected readonly marking = signal(false);
  protected readonly stale = signal<FeeCollection | null>(null);

  protected readonly money = formatMoney;
  protected readonly date = formatDate;
  protected readonly statusText = receiptStatusText;
  protected readonly statusClass = receiptStatusClass;

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    const serialNo = Number(this.route.snapshot.paramMap.get('serialNo'));
    this.receipts.get(serialNo).subscribe({
      next: (receipt) => {
        this.receipt.set(receipt);
        this.loading.set(false);
      },
      error: () => {
        this.failed.set(true);
        this.loading.set(false);
      },
    });
  }

  /** The mark was saved: show the receipt as the server has it now; the action is gone. */
  protected marked(receipt: Receipt): void {
    this.marking.set(false);
    this.receipt.set(receipt);
  }

  protected staleBalance(conflict: BalanceChanged): void {
    this.marking.set(false);
    this.stale.set(conflict.current);
  }

  /** Refresh: read the receipt again, which carries the balance version the server has now. */
  protected refresh(): void {
    this.stale.set(null);
    this.load();
  }
}
