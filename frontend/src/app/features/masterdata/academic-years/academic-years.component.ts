import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal, viewChild } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthStore } from '../../../core/auth/auth.store';
import { problemOf, STALE_VERSION } from '../../../core/http/api-problem';
import { ToastService } from '../../../core/toast/toast.service';
import { applyFieldErrors, serverMessage } from '../../../shared/forms/api-error';
import { StaleField, StaleVersionDialogComponent } from '../../../shared/forms/stale-version-dialog.component';
import { CampusTableComponent } from '../../../shared/table/campus-table.component';
import { CampusColumn } from '../../../shared/table/table-types';
import { DialogComponent } from '../../../shared/ui/dialog.component';

/**
 * Academic years (FR-010 to FR-013). The year that contains today's date carries a "Current" tag; there is no
 * Make current action and no inactive switch. A year in use cannot be deleted (FR-020); the server says so.
 */
@Component({
  selector: 'app-academic-years',
  imports: [ReactiveFormsModule, RouterLink, DialogComponent, CampusTableComponent, StaleVersionDialogComponent],
  templateUrl: './academic-years.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AcademicYearsComponent implements OnInit {
  protected readonly endpoint = '/api/academic-years';
  protected readonly columns: CampusColumn[] = [
    { field: 'id', header: 'Id', type: 'number', hidden: true },
    { field: 'version', header: 'Version', type: 'number', hidden: true },
    { field: 'name', header: 'Name' },
    { field: 'startDate', header: 'Start date', type: 'date' },
    { field: 'endDate', header: 'End date', type: 'date' },
    { field: 'current', header: 'Current', type: 'boolean', tag: 'Current' },
  ];
  protected readonly staleFields: StaleField[] = [
    { field: 'name', label: 'Name' },
    { field: 'startDate', label: 'Start date', type: 'date' },
    { field: 'endDate', label: 'End date', type: 'date' },
  ];

  private readonly http = inject(HttpClient);
  private readonly toast = inject(ToastService);
  private readonly store = inject(AuthStore);
  private readonly table = viewChild.required(CampusTableComponent);

  protected readonly formOpen = signal(false);
  protected readonly busy = signal(false);
  protected readonly stale = signal<Record<string, unknown> | null>(null);
  protected readonly deleting = signal<Record<string, unknown> | null>(null);
  protected readonly hasPreviousYears = signal(false);
  protected readonly copyResult = signal<{ divisionsCopied: number; feeRowsCopied: number } | null>(null);
  protected readonly serverMessage = serverMessage;

  /** Id and version of the year being edited; null while adding. */
  private editing: { id: number; version: number } | null = null;

  protected readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    startDate: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    endDate: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    copyFromLast: new FormControl(false, { nonNullable: true }),
  });

  constructor() {
    // With no year covering today the admin cannot go anywhere else, so the create form is ready at once (FR-013).
    if (this.store.noCurrentYear()) {
      this.add();
    }
  }

  ngOnInit(): void {
    this.refreshPreviousYears();
  }

  protected get isEdit(): boolean {
    return this.editing !== null;
  }

  protected add(): void {
    this.editing = null;
    this.form.reset({ name: '', startDate: '', endDate: '', copyFromLast: false });
    this.refreshPreviousYears();
    this.formOpen.set(true);
  }

  protected edit(row: Record<string, unknown>): void {
    this.editing = { id: row['id'] as number, version: row['version'] as number };
    this.form.reset({
      name: row['name'] as string,
      startDate: row['startDate'] as string,
      endDate: row['endDate'] as string,
      copyFromLast: false,
    });
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
      ? this.http.put(`${this.endpoint}/${editing.id}`, {
          name: values.name,
          startDate: values.startDate,
          endDate: values.endDate,
          version: editing.version,
        })
      : this.http.post<Record<string, unknown>>(this.endpoint, values);
    request.subscribe({
      next: (res) => {
        this.busy.set(false);
        this.toast.success(editing ? 'The academic year was changed.' : 'The academic year was added.');
        const body = res as Record<string, unknown> | null;
        if (!editing && body && 'divisionsCopied' in body) {
          this.copyResult.set({
            divisionsCopied: Number(body['divisionsCopied']),
            feeRowsCopied: Number(body['feeRowsCopied']),
          });
        } else {
          this.copyResult.set(null);
        }
        this.closeForm();
        this.table().reload();
        this.refreshPreviousYears();
        this.store.refresh().subscribe(); // the current year may have changed, which lifts or restores the block
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.onSaveFailed(error);
      },
    });
  }

  /** Puts the latest saved values into the form, with their version, so the next save can go through. */
  protected useLatest(): void {
    const latest = this.stale();
    if (latest && this.editing) {
      this.editing = { id: this.editing.id, version: latest['version'] as number };
      this.form.reset({
        name: latest['name'] as string,
        startDate: latest['startDate'] as string,
        endDate: latest['endDate'] as string,
        copyFromLast: false,
      });
    }
    this.stale.set(null);
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
        this.toast.success('The academic year was deleted.', 'Deleted');
        this.table().reload();
        this.refreshPreviousYears();
        this.store.refresh().subscribe();
      },
      error: () => {
        // The error interceptor shows the server's reason, for example that the year is in use.
        this.busy.set(false);
        this.deleting.set(null);
      },
    });
  }

  private refreshPreviousYears(): void {
    this.http.get<unknown[]>(this.endpoint).subscribe({
      next: (years) => this.hasPreviousYears.set(years.length > 0),
      error: () => this.hasPreviousYears.set(false),
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
