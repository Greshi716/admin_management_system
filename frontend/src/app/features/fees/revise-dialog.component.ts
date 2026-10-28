import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { DialogComponent } from '../../shared/ui/dialog.component';

/**
 * The warning before a change to a finalised fee structure is saved (F2 FR-004). Nothing has been changed when it is
 * shown. Confirm sends the same request again with `confirm=true`; Cancel and closing the dialog change nothing.
 */
@Component({
  selector: 'app-revise-dialog',
  imports: [DialogComponent],
  template: `
    <app-dialog
      header="Revise the fee structure"
      width="30rem"
      [visible]="studentCount() !== null"
      [closable]="!busy()"
      (visibleChange)="!$event && cancelled.emit()"
    >
      <div class="mb-4 rounded-card bg-warning-bg p-4 text-warning-text" role="alert" data-testid="revise-warning">
        <p class="m-0 font-bold">Every student's fee for the year will be recalculated.</p>
        <p class="mb-0 mt-1" data-testid="revise-count">
          This affects {{ studentCount() }} {{ studentCount() === 1 ? 'student' : 'students' }} of the year, including
          students who have left.
        </p>
      </div>
      <div class="flex justify-end gap-2">
        <button type="button" class="btn btn-secondary btn-outlined" [disabled]="busy()" (click)="cancelled.emit()">
          Cancel
        </button>
        <button type="button" class="btn btn-primary" [class.btn-loading]="busy()" [disabled]="busy()" (click)="confirmed.emit()">
          Confirm and recalculate
        </button>
      </div>
    </app-dialog>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReviseDialogComponent {
  /** The number of class records a revise recalculates (from the server's answer); null hides the dialog. */
  readonly studentCount = input<number | null>(null);
  readonly busy = input(false);

  readonly confirmed = output<void>();
  readonly cancelled = output<void>();
}
