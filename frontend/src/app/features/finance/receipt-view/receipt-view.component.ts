import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { formatMoney } from '../../../shared/ui/money';
import { PageSkeletonComponent } from '../../../shared/ui/page-skeleton.component';
import { Receipt, ReceiptsService, ReceiptStatus } from '../receipts.service';

const STATUS_TEXT: Record<ReceiptStatus, string> = {
  VALID: 'Valid',
  CHEQUE_NOT_CLEARED: 'Cheque not cleared',
  VOIDED: 'Voided by correction',
};

/**
 * One saved receipt exactly as stored, with its status (FR-010, FR-017). Printing and the cheque mark are added by
 * later tasks; the receipt has no edit, cancel or delete action (FR-009).
 */
@Component({
  selector: 'app-receipt-view',
  imports: [RouterLink, PageSkeletonComponent],
  template: `
    @if (loading()) {
      <app-page-skeleton />
    } @else if (failed()) {
      <p role="alert" class="rounded-control bg-error-bg px-4 py-3 text-error-text">This receipt could not be opened.</p>
    } @else if (receipt(); as r) {
      <section class="rounded-card border border-border bg-surface p-6" aria-labelledby="receipt-heading">
        <h1 id="receipt-heading" class="mb-1 text-2xl font-bold">Receipt {{ r.receiptNo }}</h1>
        <p class="mb-4 text-muted" data-testid="receipt-status">
          {{ statusText(r.status) }}@if (r.statusReason) { — {{ r.statusReason }} }
        </p>
        <dl class="grid gap-x-8 gap-y-2 sm:grid-cols-2">
          <div><dt class="text-sm text-muted">Serial no.</dt><dd data-testid="serial-no">{{ r.serialNo }}</dd></div>
          <div><dt class="text-sm text-muted">Date</dt><dd>{{ r.receiptDate }}</dd></div>
          <div>
            <dt class="text-sm text-muted">Student</dt>
            <dd><a [routerLink]="['/students', r.studentId]">{{ r.studentName }}</a> (GR {{ r.grNo }})</dd>
          </div>
          <div><dt class="text-sm text-muted">Class</dt><dd>{{ r.standardName }} {{ r.divisionName }}</dd></div>
          <div><dt class="text-sm text-muted">Academic year</dt><dd>{{ r.academicYear }}</dd></div>
          <div>
            <dt class="text-sm text-muted">Mode</dt>
            <dd>
              {{ r.paymentMode === 'CASH' ? 'Cash' : 'Cheque' }}
              @if (r.paymentMode === 'CHEQUE') { — {{ r.chequeNo }}, {{ r.bankName }}, {{ r.chequeDate }} }
            </dd>
          </div>
          <div><dt class="text-sm text-muted">Registration fee</dt><dd>{{ money(r.registrationPaid) }}</dd></div>
          <div><dt class="text-sm text-muted">Year fee</dt><dd>{{ money(r.feePaid) }}</dd></div>
          <div><dt class="text-sm text-muted">Total</dt><dd class="font-semibold" data-testid="receipt-total">{{ money(r.total) }}</dd></div>
          <div><dt class="text-sm text-muted">In words</dt><dd>{{ r.totalWords }}</dd></div>
        </dl>
      </section>
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

  protected readonly money = formatMoney;

  protected statusText(status: ReceiptStatus): string {
    return STATUS_TEXT[status];
  }

  ngOnInit(): void {
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
}
