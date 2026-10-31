import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { DialogComponent } from '../../../shared/ui/dialog.component';
import { formatMoney } from '../../../shared/ui/money';

@Component({
  selector: 'app-promote-confirm-dialog',
  imports: [DialogComponent],
  template: `
    <app-dialog
      header="Confirm promotion"
      width="32rem"
      [visible]="visible()"
      [closable]="!busy()"
      (visibleChange)="!$event && cancelled.emit()"
    >
      <div class="mb-4 space-y-3" role="alert">
        <p class="m-0 text-ink">
          Please review the summary before saving this promotion:
        </p>
        <div class="rounded-card border border-border bg-page p-4 space-y-2 text-sm">
          <div class="flex justify-between">
            <span class="text-muted">Promoted:</span>
            <span class="font-semibold text-ink" data-testid="promote-count">{{ promotedCount() }}</span>
          </div>
          <div class="flex justify-between">
            <span class="text-muted">Stay back (Detained):</span>
            <span class="font-semibold text-ink" data-testid="detained-count">{{ detainedCount() }}</span>
          </div>
          <div class="flex justify-between">
            <span class="text-muted">Marked Left:</span>
            <span class="font-semibold text-ink" data-testid="left-count">{{ leftCount() }}</span>
          </div>
          <div class="flex justify-between border-t border-border pt-2">
            <span class="text-muted">Total fee carried forward:</span>
            <span class="font-bold text-ink" data-testid="carried-total">₹ {{ money(carriedTotal()) }}</span>
          </div>
        </div>
        <p class="m-0 text-xs text-muted">
          Carried amounts will become brought-forward fees in the new year. A wrong promotion can only be corrected by a developer runbook.
        </p>
      </div>
      <div class="flex justify-end gap-2">
        <button
          type="button"
          class="btn btn-secondary btn-outlined min-h-[44px]"
          data-testid="cancel-promote"
          [disabled]="busy()"
          (click)="cancelled.emit()"
        >
          Cancel
        </button>
        <button
          type="button"
          class="btn btn-primary min-h-[44px]"
          data-testid="confirm-promote"
          [class.btn-loading]="busy()"
          [disabled]="busy()"
          (click)="confirmed.emit()"
        >
          Confirm and promote
        </button>
      </div>
    </app-dialog>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PromoteConfirmDialogComponent {
  readonly visible = input(false);
  readonly busy = input(false);
  readonly promotedCount = input(0);
  readonly detainedCount = input(0);
  readonly leftCount = input(0);
  readonly carriedTotal = input(0);

  readonly confirmed = output<void>();
  readonly cancelled = output<void>();

  protected readonly money = formatMoney;
}
