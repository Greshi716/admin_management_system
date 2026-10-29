import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { applyFieldErrors, serverMessage } from '../../../shared/forms/api-error';
import { DialogComponent } from '../../../shared/ui/dialog.component';
import { formatMoney } from '../../../shared/ui/money';
import { BalanceChanged, Receipt, receiptConflictOf, ReceiptRefusal, ReceiptsService } from '../receipts.service';

/** The most characters the server accepts for the reason (FR-013). */
export const MAX_REASON = 200;

/**
 * Marks a Cheque receipt "Cheque not cleared" (FR-013). The reason is required and is shown beside its field when the
 * server refuses it. The mark is one-way, so the dialog says what will happen before it is confirmed. If the balance
 * changed after the receipt was opened, the server answers BALANCE_CHANGED and the parent shows the balance-changed pop-up.
 */
@Component({
  selector: 'app-receipt-mark-dialog',
  imports: [DialogComponent, ReactiveFormsModule],
  template: `
    <app-dialog header="Cheque not cleared" width="30rem" [visible]="receipt() !== null" [closable]="!busy()" (visibleChange)="!$event && cancelled.emit()">
      @if (receipt(); as r) {
        <p class="mb-3" data-testid="mark-explain">
          {{ money(r.total) }} will be added back to the balance of {{ r.studentName }} and "Paid up to" is recalculated.
          This cannot be undone. If the cheque is paid again, enter a new receipt.
        </p>
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <label for="mark-reason" class="mb-1 block text-sm font-semibold">Reason (required, at most {{ max }} characters)</label>
          <textarea id="mark-reason" class="input w-full" rows="3" formControlName="reason" [attr.maxlength]="null" [attr.aria-invalid]="serverMessage(form.controls.reason) ? 'true' : null"></textarea>
          @if (serverMessage(form.controls.reason); as message) {
            <p class="mb-0 mt-1 text-sm text-error-text" role="alert" data-testid="mark-reason-error">{{ message }}</p>
          }
          @if (note(); as text) {
            <p class="mb-0 mt-2 text-sm text-error-text" role="alert" data-testid="mark-note">{{ text }}</p>
          }
          <div class="mt-4 flex justify-end gap-2">
            <button type="button" class="btn btn-secondary btn-outlined" [disabled]="busy()" (click)="cancelled.emit()">Cancel</button>
            <button type="submit" class="btn btn-danger" [class.btn-loading]="busy()" [disabled]="busy()" data-testid="mark-confirm">
              Mark as not cleared
            </button>
          </div>
        </form>
      }
    </app-dialog>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReceiptMarkDialogComponent {
  private readonly receipts = inject(ReceiptsService);
  private readonly fb = inject(FormBuilder).nonNullable;

  /** The receipt to mark; null keeps the dialog closed. */
  readonly receipt = input<Receipt | null>(null);

  /** The marked receipt as the server now has it. */
  readonly marked = output<Receipt>();
  readonly cancelled = output<void>();
  /** The balance changed after the receipt was opened; carries the fresh context. */
  readonly balanceChanged = output<BalanceChanged>();

  protected readonly money = formatMoney;
  protected readonly serverMessage = serverMessage;
  protected readonly max = MAX_REASON;
  protected readonly busy = signal(false);
  protected readonly note = signal<string | null>(null);
  protected readonly form = this.fb.group({ reason: this.fb.control('') });

  protected submit(): void {
    const r = this.receipt();
    if (!r || this.busy()) {
      return;
    }
    this.note.set(null);
    this.busy.set(true);
    this.receipts.markNotCleared(r.serialNo, this.form.controls.reason.value, r.balanceVersion).subscribe({
      next: (marked) => {
        this.busy.set(false);
        this.form.reset({ reason: '' });
        this.marked.emit(marked);
      },
      error: (error: unknown) => {
        this.busy.set(false);
        const conflict = receiptConflictOf(error);
        if (conflict instanceof BalanceChanged) {
          this.balanceChanged.emit(conflict);
          return;
        }
        const unmatched = applyFieldErrors(this.form, error);
        if (conflict instanceof ReceiptRefusal) {
          this.note.set(conflict.message);
        } else if (unmatched.length > 0) {
          this.note.set(unmatched.map((m) => m.message).join(' '));
        }
      },
    });
  }
}
