import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { problemOf, STALE_VERSION } from '../../../core/http/api-problem';
import { ToastService } from '../../../core/toast/toast.service';
import { applyFieldErrors, serverMessage } from '../../../shared/forms/api-error';
import { StaleField, StaleVersionDialogComponent } from '../../../shared/forms/stale-version-dialog.component';
import { DialogComponent } from '../../../shared/ui/dialog.component';
import { Enrollment, PickLists, StudentsService } from '../students.service';

/**
 * Changes the standard, division and category of one year's class record (FR-006). The choices are those of that
 * record's own academic year; a value the record already holds stays in the list even when it is inactive. A save
 * that finds the record changed elsewhere (FR-018) shows the latest values in the shared stale-version dialog.
 */
@Component({
  selector: 'app-class-record-dialog',
  imports: [ReactiveFormsModule, DialogComponent, StaleVersionDialogComponent],
  template: `
    <app-dialog
      [header]="'Change class record ' + (record()?.academicYearName ?? '')"
      width="28rem"
      [visible]="record() !== null && stale() === null"
      (visibleChange)="!$event && stale() === null && closed.emit()"
    >
      @if (lists(); as pick) {
        <form [formGroup]="form" (ngSubmit)="save()" novalidate class="grid gap-3">
          <div>
            <label class="mb-1 block text-sm font-semibold" for="crStandard">Standard *</label>
            <select id="crStandard" class="input w-full" formControlName="standardId">
              @for (standard of pick.standards; track standard.id) {
                <option [ngValue]="standard.id">{{ standard.name }}</option>
              }
            </select>
            @if (message(form.controls.standardId); as text) {
              <p class="mt-1 text-sm text-error-text" role="alert">{{ text }}</p>
            }
          </div>
          <div>
            <label class="mb-1 block text-sm font-semibold" for="crDivision">Division *</label>
            <select id="crDivision" class="input w-full" formControlName="divisionId">
              <option [ngValue]="null" disabled>Choose</option>
              @for (division of divisions(); track division.id) {
                <option [ngValue]="division.id">{{ division.name }}</option>
              }
            </select>
            @if (message(form.controls.divisionId); as text) {
              <p class="mt-1 text-sm text-error-text" role="alert">{{ text }}</p>
            }
          </div>
          <div>
            <label class="mb-1 block text-sm font-semibold" for="crCategory">Category *</label>
            <select id="crCategory" class="input w-full" formControlName="categoryId">
              @for (category of pick.categories; track category.id) {
                <option [ngValue]="category.id">{{ category.name }}</option>
              }
            </select>
            @if (message(form.controls.categoryId); as text) {
              <p class="mt-1 text-sm text-error-text" role="alert">{{ text }}</p>
            }
          </div>
          <div class="mt-2 flex justify-end gap-2">
            <button type="button" class="btn btn-secondary btn-outlined" (click)="closed.emit()">Cancel</button>
            <button type="submit" class="btn btn-primary" [class.btn-loading]="busy()" [disabled]="busy()">Save</button>
          </div>
        </form>
      }
    </app-dialog>
    <app-stale-version-dialog
      [latest]="stale()"
      [fields]="staleFields"
      (useLatest)="useLatest()"
      (keepMine)="keepMine()"
    />
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClassRecordDialogComponent {
  private readonly students = inject(StudentsService);
  private readonly toast = inject(ToastService);

  /** The record to change; null keeps the dialog closed. */
  readonly record = input<Enrollment | null>(null);
  readonly studentId = input.required<number>();

  /** The record was saved (or the latest values were chosen): the profile must reload. */
  readonly changed = output<void>();
  readonly closed = output<void>();

  protected readonly staleFields: StaleField[] = [
    { field: 'stdDiv', label: 'Std / Div' },
    { field: 'categoryName', label: 'Category' },
  ];
  protected readonly busy = signal(false);
  protected readonly lists = signal<PickLists | null>(null);
  protected readonly stale = signal<Record<string, unknown> | null>(null);
  private readonly chosenStandard = signal<number | null>(null);
  private version = 0;

  protected readonly form = new FormGroup({
    standardId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    divisionId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    categoryId: new FormControl<number | null>(null, { validators: [Validators.required] }),
  });

  protected readonly divisions = computed(() => {
    const standard = this.chosenStandard();
    return (this.lists()?.divisions ?? []).filter((division) => division.standardId === standard);
  });

  constructor() {
    this.form.controls.standardId.valueChanges.subscribe((standard) => {
      if (standard !== this.chosenStandard()) {
        this.chosenStandard.set(standard);
        this.form.controls.divisionId.reset(null);
      }
    });
    effect(() => {
      const record = this.record();
      untracked(() => (record ? this.open(record) : this.lists.set(null)));
    });
  }

  protected message(control: FormControl): string | null {
    return serverMessage(control);
  }

  protected save(): void {
    const record = this.record();
    if (!record || this.busy()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.busy.set(true);
    this.students
      .updateEnrollment(this.studentId(), record.academicYearId, {
        version: this.version,
        standardId: value.standardId as number,
        divisionId: value.divisionId as number,
        categoryId: value.categoryId as number,
      })
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.toast.success('The class record was changed.');
          this.changed.emit();
        },
        error: (error: unknown) => {
          this.busy.set(false);
          if (error instanceof HttpErrorResponse && problemOf(error.error)?.code === STALE_VERSION) {
            this.stale.set((problemOf(error.error)?.current ?? {}) as Record<string, unknown>);
            return;
          }
          const unmatched = applyFieldErrors(this.form, error);
          if (unmatched.length > 0) {
            this.toast.error(unmatched.map((e) => e.message).join(' '));
          }
        },
      });
  }

  /** Throws away what was typed: the profile reloads and shows the latest record. */
  protected useLatest(): void {
    this.stale.set(null);
    this.changed.emit();
  }

  /** Keeps the typed values: the next save is made against the latest version. */
  protected keepMine(): void {
    const latest = this.stale();
    if (latest && typeof latest['version'] === 'number') {
      this.version = latest['version'];
    }
    this.stale.set(null);
  }

  private open(record: Enrollment): void {
    this.version = record.version;
    this.stale.set(null);
    this.chosenStandard.set(record.standardId);
    this.form.reset({ standardId: record.standardId, divisionId: record.divisionId, categoryId: record.categoryId });
    this.students.pickLists(record.academicYearId, this.studentId()).subscribe({
      next: (lists) => this.lists.set(lists),
      error: () => this.closed.emit(), // the error interceptor has shown the reason
    });
  }
}
