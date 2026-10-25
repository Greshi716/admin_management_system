import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal, viewChild } from '@angular/core';
import { forkJoin } from 'rxjs';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { problemOf, STALE_VERSION } from '../../../core/http/api-problem';
import { ToastService } from '../../../core/toast/toast.service';
import { applyFieldErrors, serverMessage } from '../../../shared/forms/api-error';
import { StaleField, StaleVersionDialogComponent } from '../../../shared/forms/stale-version-dialog.component';
import { CampusTableComponent } from '../../../shared/table/campus-table.component';
import { CampusColumn } from '../../../shared/table/table-types';
import { DialogComponent } from '../../../shared/ui/dialog.component';
import { PageSkeletonComponent } from '../../../shared/ui/page-skeleton.component';

interface Choice {
  id: number;
  name: string;
}

/**
 * Divisions (FR-016, FR-017): a division belongs to one academic year and one standard. The form offers every
 * academic year but only active standards. A division in use cannot be deleted; the admin marks it inactive.
 */
@Component({
  selector: 'app-divisions',
  imports: [ReactiveFormsModule, DialogComponent, CampusTableComponent, StaleVersionDialogComponent, PageSkeletonComponent],
  templateUrl: './divisions.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DivisionsComponent {
  protected readonly endpoint = '/api/divisions';
  protected readonly columns: CampusColumn[] = [
    { field: 'id', header: 'Id', type: 'number', hidden: true },
    { field: 'version', header: 'Version', type: 'number', hidden: true },
    { field: 'academicYearId', header: 'Academic year id', type: 'number', hidden: true },
    { field: 'standardId', header: 'Standard id', type: 'number', hidden: true },
    { field: 'academicYearName', header: 'Academic year' },
    { field: 'standardName', header: 'Standard' },
    { field: 'name', header: 'Division' },
    { field: 'active', header: 'Active', type: 'boolean' },
  ];
  protected readonly staleFields: StaleField[] = [
    { field: 'academicYearName', label: 'Academic year' },
    { field: 'standardName', label: 'Standard' },
    { field: 'name', label: 'Division' },
  ];

  private readonly http = inject(HttpClient);
  private readonly toast = inject(ToastService);
  private readonly table = viewChild.required(CampusTableComponent);

  protected readonly formOpen = signal(false);
  protected readonly busy = signal(false);
  protected readonly loadingChoices = signal(false);
  protected readonly years = signal<Choice[]>([]);
  protected readonly standards = signal<Choice[]>([]);
  protected readonly stale = signal<Record<string, unknown> | null>(null);
  protected readonly deleting = signal<Record<string, unknown> | null>(null);
  protected readonly serverMessage = serverMessage;

  private editing: { id: number; version: number } | null = null;

  protected readonly form = new FormGroup({
    academicYearId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    standardId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  protected get isEdit(): boolean {
    return this.editing !== null;
  }

  protected add(): void {
    this.editing = null;
    this.form.reset({ academicYearId: null, standardId: null, name: '' });
    this.loadChoices();
    this.formOpen.set(true);
  }

  protected edit(row: Record<string, unknown>): void {
    this.editing = { id: row['id'] as number, version: row['version'] as number };
    this.fill(row);
    this.loadChoices();
    this.formOpen.set(true);
  }

  protected closeForm(): void {
    this.formOpen.set(false);
    this.stale.set(null);
  }

  protected save(): void {
    if (this.busy()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const values = this.form.getRawValue();
    const editing = this.editing;
    this.busy.set(true);
    const request = editing
      ? this.http.put(`${this.endpoint}/${editing.id}`, { ...values, version: editing.version })
      : this.http.post(this.endpoint, values);
    request.subscribe({
      next: () => {
        this.busy.set(false);
        this.toast.success(editing ? 'The division was changed.' : 'The division was added.');
        this.closeForm();
        this.table().reload();
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.onSaveFailed(error);
      },
    });
  }

  protected useLatest(): void {
    const latest = this.stale();
    if (latest && this.editing) {
      this.editing = { id: this.editing.id, version: latest['version'] as number };
      this.fill(latest);
    }
    this.stale.set(null);
  }

  protected setActive(row: Record<string, unknown>, active: boolean): void {
    this.http.put(`${this.endpoint}/${row['id']}/active`, { active, version: row['version'] }).subscribe({
      next: () => {
        this.toast.success(active ? 'The division is active again.' : 'The division was marked inactive.');
        this.table().reload();
      },
      // The interceptor has shown the reason; reload so the row shows the latest values.
      error: () => this.table().reload(),
    });
  }

  protected askDelete(row: Record<string, unknown>): void {
    this.deleting.set(row);
  }

  protected confirmDelete(): void {
    const row = this.deleting();
    if (!row) {
      return;
    }
    this.busy.set(true);
    this.http.delete(`${this.endpoint}/${row['id']}`).subscribe({
      next: () => {
        this.busy.set(false);
        this.deleting.set(null);
        this.toast.success('The division was deleted.', 'Deleted');
        this.table().reload();
      },
      error: () => {
        // The error interceptor shows the server's reason, for example that the division is in use.
        this.busy.set(false);
        this.deleting.set(null);
      },
    });
  }

  /** All academic years, and the active standards only (FR-016). */
  private loadChoices(): void {
    this.loadingChoices.set(true);
    forkJoin({
      years: this.http.get<Choice[]>('/api/academic-years'),
      standards: this.http.get<Choice[]>('/api/standards'),
    }).subscribe({
      next: ({ years, standards }) => {
        this.years.set(years);
        this.standards.set(standards);
        this.loadingChoices.set(false);
      },
      error: () => this.loadingChoices.set(false), // the error interceptor has shown the reason
    });
  }

  private fill(values: Record<string, unknown>): void {
    this.form.reset({
      academicYearId: values['academicYearId'] as number,
      standardId: values['standardId'] as number,
      name: values['name'] as string,
    });
  }

  private onSaveFailed(error: unknown): void {
    if (error instanceof HttpErrorResponse && problemOf(error.error)?.code === STALE_VERSION) {
      this.stale.set((problemOf(error.error)?.current ?? {}) as Record<string, unknown>);
      return;
    }
    applyFieldErrors(this.form, error);
  }
}
