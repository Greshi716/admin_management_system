import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { formatMoney } from '../../../shared/ui/money';
import { DialogComponent } from '../../../shared/ui/dialog.component';
import { formatDate } from '../finance-format';

/** What the admin checks before the receipt is saved (FR-008). The total and the words come from the server preview. */
export interface ReceiptConfirmDetails {
  studentName: string;
  grNo: string;
  stdDiv: string;
  receiptNo: string;
  receiptDate: string;
  lines: { label: string; amount: number }[];
  total: number;
  totalWords: string;
  mode: 'Cash' | 'Cheque';
  chequeNo: string | null;
  chequeDate: string | null;
  bankName: string | null;
}

/**
 * The confirmation before Save (FR-008): the student, receipt no., date, each amount and the total. It is the
 * safeguard, because a saved receipt cannot be changed (FR-009). Cancel returns to the form with the values kept.
 */
@Component({
  selector: 'app-receipt-confirm-dialog',
  imports: [DialogComponent],
  template: `
    <app-dialog
      header="Check the receipt before saving"
      width="32rem"
      [visible]="details() !== null"
      [closable]="!busy()"
      (visibleChange)="!$event && cancelled.emit()"
    >
      @if (details(); as d) {
        <p class="mb-3 text-muted">A saved receipt cannot be changed or cancelled. Check every value.</p>
        <dl class="mb-4 grid grid-cols-[auto_1fr] gap-x-4 gap-y-1" data-testid="confirm-details">
          <dt class="font-semibold">Student</dt>
          <dd data-testid="confirm-student">{{ d.studentName }} (GR {{ d.grNo }}, {{ d.stdDiv }})</dd>
          <dt class="font-semibold">Receipt no.</dt>
          <dd data-testid="confirm-receipt-no">{{ d.receiptNo }}</dd>
          <dt class="font-semibold">Date</dt>
          <dd data-testid="confirm-date">{{ date(d.receiptDate) }}</dd>
          @for (line of d.lines; track line.label) {
            <dt class="font-semibold">{{ line.label }}</dt>
            <dd class="text-right tabular-nums">{{ money(line.amount) }}</dd>
          }
          <dt class="font-semibold">Total</dt>
          <dd class="text-right text-lg font-bold tabular-nums" data-testid="confirm-total">{{ money(d.total) }}</dd>
          <dt class="font-semibold">In words</dt>
          <dd data-testid="confirm-words">{{ d.totalWords }}</dd>
          <dt class="font-semibold">Mode</dt>
          <dd>
            {{ d.mode }}
            @if (d.mode === 'Cheque') {
              — {{ d.chequeNo }}, {{ d.bankName }}, {{ date(d.chequeDate) }}
            }
          </dd>
        </dl>
        <div class="flex justify-end gap-2">
          <button type="button" class="btn btn-secondary btn-outlined" [disabled]="busy()" (click)="cancelled.emit()">Cancel</button>
          <button type="button" class="btn btn-primary" [class.btn-loading]="busy()" [disabled]="busy()" (click)="confirmed.emit()">
            Confirm and save
          </button>
        </div>
      }
    </app-dialog>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReceiptConfirmDialogComponent {
  /** The values to check; null keeps the dialog closed. */
  readonly details = input<ReceiptConfirmDetails | null>(null);
  readonly busy = input(false);

  readonly confirmed = output<void>();
  readonly cancelled = output<void>();

  protected readonly money = formatMoney;
  protected readonly date = formatDate;
}
