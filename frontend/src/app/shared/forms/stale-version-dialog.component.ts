import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { Button } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { formatCell } from '../table/format-cell';

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
  imports: [Dialog, Button],
  template: `
    <p-dialog
      header="This record was changed"
      [modal]="true"
      [draggable]="false"
      [resizable]="false"
      [visible]="latest() !== null"
      (visibleChange)="!$event && keepMine.emit()"
      [style]="{ width: '28rem', maxWidth: '95vw' }"
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
        <p-button label="Keep what I typed" severity="secondary" [outlined]="true" (onClick)="keepMine.emit()" />
        <p-button label="Use the latest values" (onClick)="useLatest.emit()" />
      </div>
    </p-dialog>
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
