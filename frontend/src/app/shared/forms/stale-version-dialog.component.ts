import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { formatCell } from '../table/format-cell';
import { DialogComponent } from '../ui/dialog.component';

/** One value of the latest record, shown by label. */
export interface StaleField {
  field: string;
  label: string;
  type?: 'text' | 'number' | 'date' | 'boolean';
}

/**
 * Shown when a save is refused because someone else changed the record first (FR-046). It lists the latest
 * saved values; the admin can load them into the form and save again, or close and keep what was typed.
 */
@Component({
  selector: 'app-stale-version-dialog',
  imports: [DialogComponent],
  template: `
    <app-dialog
      header="This record was changed"
      width="28rem"
      [visible]="latest() !== null"
      (visibleChange)="!$event && keepMine.emit()"
    >
      <p class="mb-3">
        Someone saved a change to this record after you opened it. These are the latest saved values:
      </p>
      <dl class="mb-4 grid grid-cols-[auto_1fr] gap-x-4 gap-y-1" data-testid="latest-values">
        @for (item of items(); track item.field) {
          <dt class="font-semibold">{{ item.label }}</dt>
          <dd>{{ item.text }}</dd>
        }
      </dl>
      <div class="flex justify-end gap-2">
        <button type="button" class="btn btn-secondary btn-outlined" (click)="keepMine.emit()">Keep what I typed</button>
        <button type="button" class="btn btn-primary" (click)="useLatest.emit()">Use the latest values</button>
      </div>
    </app-dialog>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StaleVersionDialogComponent {
  /** The latest record from the server (the `current` of the STALE_VERSION answer); null hides the dialog. */
  readonly latest = input<Record<string, unknown> | null>(null);
  readonly fields = input.required<StaleField[]>();

  readonly useLatest = output<void>();
  readonly keepMine = output<void>();

  protected readonly items = computed(() => {
    const latest = this.latest();
    return this.fields().map((item) => ({
      field: item.field,
      label: item.label,
      text: latest ? formatCell(latest[item.field], item.type ?? 'text') : '',
    }));
  });
}
