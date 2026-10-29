import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { DialogComponent } from '../../../shared/ui/dialog.component';
import { FeeCollection } from '../receipts.service';

/**
 * Shown when the server says the balance changed after this screen loaded (FR-016). Nothing was saved. The only way
 * on is Refresh, which loads the balance the server has now; what the admin typed stays in the form.
 */
@Component({
  selector: 'app-balance-changed-dialog',
  imports: [DialogComponent],
  template: `
    <app-dialog header="The balance has changed" width="28rem" [visible]="latest() !== null" [closable]="false">
      <p class="mb-4" data-testid="balance-changed-text">
        Another receipt or change was saved for this student after this screen was opened. Nothing was saved. Refresh to
        see the balance as it is now.
      </p>
      <div class="flex justify-end">
        <button type="button" class="btn btn-primary" (click)="refresh.emit()">Refresh</button>
      </div>
    </app-dialog>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BalanceChangedDialogComponent {
  /** The fresh context from the 409 answer; null keeps the dialog closed. */
  readonly latest = input<FeeCollection | null>(null);

  readonly refresh = output<void>();
}
