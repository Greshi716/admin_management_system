import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal, viewChild } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Button } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { InputText } from 'primeng/inputtext';
import { problemOf, STALE_VERSION } from '../../../core/http/api-problem';
import { ToastService } from '../../../core/toast/toast.service';
import { applyFieldErrors, serverMessage } from '../../../shared/forms/api-error';
import { StaleField, StaleVersionDialogComponent } from '../../../shared/forms/stale-version-dialog.component';
import { CampusTableComponent } from '../../../shared/table/campus-table.component';
import { CampusColumn } from '../../../shared/table/table-types';

/**
 * Student categories (FR-018 to FR-021). "Affects fees" has no default: the admin has to choose Yes or No, so a
 * category is never saved with a fee setting nobody chose.
 */
@Component({
  selector: 'app-student-categories',
  imports: [ReactiveFormsModule, Button, Dialog, InputText, CampusTableComponent, StaleVersionDialogComponent],
  templateUrl: './student-categories.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StudentCategoriesComponent {
  protected readonly endpoint = '/api/student-categories';
  protected readonly columns: CampusColumn[] = [
    { field: 'id', header: 'Id', type: 'number', hidden: true },
    { field: 'version', header: 'Version', type: 'number', hidden: true },
    { field: 'code', header: 'Code' },
    { field: 'name', header: 'Name' },
    { field: 'affectsFees', header: 'Affects fees', type: 'boolean' },
    { field: 'active', header: 'Active', type: 'boolean' },
  ];
  protected readonly staleFields: StaleField[] = [
    { field: 'code', label: 'Code' },
    { field: 'name', label: 'Name' },
    { field: 'affectsFees', label: 'Affects fees', type: 'boolean' },
  ];

  private readonly http = inject(HttpClient);
  private readonly toast = inject(ToastService);
  private readonly table = viewChild.required(CampusTableComponent);

  protected readonly formOpen = signal(false);
  protected readonly busy = signal(false);
  protected readonly stale = signal<Record<string, unknown> | null>(null);
  protected readonly deleting = signal<Record<string, unknown> | null>(null);
  protected readonly serverMessage = serverMessage;

  private editing: { id: number; version: number } | null = null;

  protected readonly form = new FormGroup({
    code: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    affectsFees: new FormControl<boolean | null>(null, { validators: [Validators.required] }),
  });

  protected get isEdit(): boolean {
    return this.editing !== null;
  }

  protected add(): void {
    this.editing = null;
    this.form.reset({ code: '', name: '', affectsFees: null });
    this.formOpen.set(true);
  }

  protected edit(row: Record<string, unknown>): void {
    this.editing = { id: row['id'] as number, version: row['version'] as number };
    this.fill(row);
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
        this.toast.success(editing ? 'The category was changed.' : 'The category was added.');
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
        this.toast.success(active ? 'The category is active again.' : 'The category was marked inactive.');
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
        this.toast.success('The category was deleted.', 'Deleted');
        this.table().reload();
      },
      error: () => {
        // The error interceptor shows the server's reason, for example that the category is in use.
        this.busy.set(false);
        this.deleting.set(null);
      },
    });
  }

  private fill(values: Record<string, unknown>): void {
    this.form.reset({
      code: values['code'] as string,
      name: values['name'] as string,
      affectsFees: values['affectsFees'] as boolean,
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
