import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { problemOf } from '../../core/http/api-problem';
import { ToastService } from '../../core/toast/toast.service';
import { applyFieldErrors, serverMessage } from '../../shared/forms/api-error';
import { DialogComponent } from '../../shared/ui/dialog.component';
import { formatCell } from '../../shared/table/format-cell';
import { formatMoney, parseMoney } from '../../shared/ui/money';
import {
  FeeChoices,
  FeeRow,
  FeeRowInput,
  FeesService,
  FeeStructure,
  feeConflictOf,
  ReviseConfirmationRequired,
  StaleStructure,
} from './fees.service';
import { ReviseDialogComponent } from './revise-dialog.component';

interface YearOption {
  id: number;
  name: string;
  current: boolean;
}

/** One row of the table: a saved row or a new one that has no id yet. */
export interface RowEditor {
  id: number | null;
  version: number | null;
  busy: boolean;
  form: FormGroup<{
    standardId: FormControl<number | null>;
    categoryId: FormControl<number | null>;
    amount: FormControl<string>;
    registrationFee: FormControl<string>;
    dueDate: FormControl<string>;
  }>;
}

/**
 * Fee structure (F2 FR-001, FR-002, FR-003): the yearly fee, registration fee and due date for each standard and
 * category of one academic year. Amounts are shown as the server sends them with two decimals and Indian grouping; the
 * browser never adds or rounds them (FR-011). A Draft year is edited freely; Finalise gives every student a fee.
 */
@Component({
  selector: 'app-fee-structure',
  imports: [ReactiveFormsModule, DialogComponent, ReviseDialogComponent],
  templateUrl: './fee-structure.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FeeStructureComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly fees = inject(FeesService);
  private readonly toast = inject(ToastService);

  protected readonly years = signal<YearOption[]>([]);
  protected readonly yearId = signal<number | null>(null);
  protected readonly structure = signal<FeeStructure | null>(null);
  protected readonly choices = signal<FeeChoices>({ standards: [], categories: [] });
  protected readonly editors = signal<RowEditor[]>([]);
  protected readonly loadFailed = signal(false);
  protected readonly finalising = signal(false);
  protected readonly finaliseBusy = signal(false);
  /** Why a finalise was refused (for example no rows), shown in the card; the toast only says fields need fixing. */
  protected readonly finaliseError = signal<string | null>(null);
  protected readonly deleting = signal<RowEditor | null>(null);
  protected readonly deleteBusy = signal(false);
  /** True while the admin revises a finalised year: rows can then be changed, each change after a warning (FR-004). */
  protected readonly revising = signal(false);
  /** The revise warning waiting for an answer: how many students it touches and the request to send again with confirm. */
  protected readonly reviseAsk = signal<{ studentCount: number; action: () => void } | null>(null);

  protected readonly serverMessage = serverMessage;
  protected readonly money = formatMoney;
  protected readonly formatDate = (value: string | null | undefined) => formatCell(value, 'date');

  protected readonly isFinalised = computed(() => this.structure()?.status === 'Finalised');
  /** Rows can be changed in a Draft year, and in a finalised year only while it is being revised. */
  protected readonly editable = computed(() => !this.isFinalised() || this.revising());

  ngOnInit(): void {
    this.http
      .post<{ rows: { id: number; name: string; current: boolean }[] }>('/api/academic-years/search', {
        first: 0,
        rows: 100,
        filters: {},
      })
      .subscribe({
        next: (page) => {
          const years = page.rows.map((row) => ({ id: row.id, name: row.name, current: !!row.current }));
          this.years.set(years);
          const first = years.find((year) => year.current) ?? years[0];
          if (first) {
            this.chooseYear(first.id);
          }
        },
        error: () => this.loadFailed.set(true),
      });
  }

  protected chooseYear(id: number): void {
    this.yearId.set(id);
    this.revising.set(false);
    this.load(id);
  }

  protected onYearPicked(event: Event): void {
    this.chooseYear(Number((event.target as HTMLSelectElement).value));
  }

  /** Loads the structure and the choices of a year and rebuilds the table from the server's values. */
  protected load(id: number): void {
    this.fees.choices(id).subscribe({
      next: (choices) => this.choices.set(choices),
      error: () => this.loadFailed.set(true),
    });
    this.fees.structure(id).subscribe({
      next: (structure) => this.show(structure),
      error: () => this.loadFailed.set(true),
    });
  }

  protected addRow(): void {
    this.editors.update((rows) => [...rows, this.editorOf(null)]);
  }

  protected save(editor: RowEditor, confirm = false): void {
    const structure = this.structure();
    const yearId = this.yearId();
    if (editor.busy || !structure || yearId === null) {
      return;
    }
    const input = this.inputOf(editor, structure);
    if (!input) {
      return;
    }
    this.setBusy(editor, true);
    const request =
      editor.id === null
        ? this.fees.addRow(yearId, input, confirm)
        : this.fees.editRow(yearId, editor.id, { ...input, version: editor.version ?? undefined }, confirm);
    request.subscribe({
      next: () => {
        const done = editor.id === null ? 'The fee row was added.' : 'The fee row was saved.';
        this.toast.success(confirm ? `${done} Every student's fee was recalculated.` : done);
        this.load(yearId);
      },
      error: (error: unknown) => {
        this.setBusy(editor, false);
        const conflict = feeConflictOf(error);
        if (conflict instanceof ReviseConfirmationRequired) {
          this.askRevise(conflict.studentCount, () => this.save(editor, true));
          return;
        }
        this.onSaveFailed(editor, error);
      },
    });
  }

  protected askDelete(editor: RowEditor): void {
    if (editor.id === null) {
      this.editors.update((rows) => rows.filter((row) => row !== editor));
      return;
    }
    if (this.isFinalised()) {
      this.runDelete(editor, false); // the revise warning is the confirmation
      return;
    }
    this.deleting.set(editor);
  }

  protected confirmDelete(): void {
    const editor = this.deleting();
    if (editor) {
      this.runDelete(editor, false);
    }
  }

  protected startRevising(): void {
    this.revising.set(true);
  }

  protected stopRevising(): void {
    this.revising.set(false);
    const yearId = this.yearId();
    if (yearId !== null) {
      this.load(yearId); // drop edits that were typed but not saved
    }
  }

  protected confirmRevise(): void {
    const ask = this.reviseAsk();
    this.reviseAsk.set(null);
    ask?.action();
  }

  protected cancelRevise(): void {
    this.reviseAsk.set(null); // nothing was changed; the typed values stay in the row
  }

  private askRevise(studentCount: number, action: () => void): void {
    this.reviseAsk.set({ studentCount, action });
  }

  private runDelete(editor: RowEditor, confirm: boolean): void {
    const structure = this.structure();
    const yearId = this.yearId();
    if (editor.id === null || !structure || yearId === null) {
      return;
    }
    this.deleteBusy.set(true);
    this.fees.deleteRow(yearId, editor.id, editor.version ?? 0, structure.version, confirm).subscribe({
      next: () => {
        this.deleteBusy.set(false);
        this.deleting.set(null);
        this.toast.success(
          confirm ? "The fee row was deleted. Every student's fee was recalculated." : 'The fee row was deleted.',
          'Deleted',
        );
        this.load(yearId);
      },
      error: (error: unknown) => {
        this.deleteBusy.set(false);
        this.deleting.set(null);
        const conflict = feeConflictOf(error);
        if (conflict instanceof ReviseConfirmationRequired) {
          this.askRevise(conflict.studentCount, () => this.runDelete(editor, true));
        } else if (conflict instanceof StaleStructure) {
          this.load(yearId);
        }
      },
    });
  }

  protected askFinalise(): void {
    this.finaliseError.set(null);
    this.finalising.set(true);
  }

  protected confirmFinalise(): void {
    const structure = this.structure();
    const yearId = this.yearId();
    if (!structure || yearId === null) {
      return;
    }
    this.finaliseBusy.set(true);
    this.fees.finalise(yearId, structure.version).subscribe({
      next: (finalised) => {
        this.finaliseBusy.set(false);
        this.finalising.set(false);
        this.revising.set(false);
        this.toast.success('The fee structure is finalised. Every student of the year now has a fee.', 'Finalised');
        this.show(finalised);
      },
      error: (error: unknown) => {
        this.finaliseError.set(
          error instanceof HttpErrorResponse ? (problemOf(error.error)?.fieldErrors?.[0]?.message ?? null) : null,
        );
        this.finaliseBusy.set(false);
        this.finalising.set(false);
        if (feeConflictOf(error) instanceof StaleStructure) {
          this.load(yearId);
        }
      },
    });
  }

  protected standardName(id: number | null): string {
    return this.choices().standards.find((standard) => standard.id === id)?.name ?? '';
  }

  private show(structure: FeeStructure): void {
    this.structure.set(structure);
    this.editors.set(structure.rows.map((row) => this.editorOf(row)));
  }

  private editorOf(row: FeeRow | null): RowEditor {
    const text = (value: number | null | undefined) => (value === null || value === undefined ? '' : value.toFixed(2));
    return {
      id: row?.id ?? null,
      version: row?.version ?? null,
      busy: false,
      form: new FormGroup({
        standardId: new FormControl<number | null>(row?.standardId ?? null),
        categoryId: new FormControl<number | null>(row?.categoryId ?? null),
        amount: new FormControl(text(row?.amount), { nonNullable: true }),
        registrationFee: new FormControl(text(row?.registrationFee), { nonNullable: true }),
        dueDate: new FormControl(row?.dueDate ?? '', { nonNullable: true }),
      }),
    };
  }

  /** The request for a row, or null (with the message beside the field) when a money box does not hold a number. */
  private inputOf(editor: RowEditor, structure: FeeStructure): FeeRowInput | null {
    const { standardId, categoryId, amount, registrationFee, dueDate } = editor.form.getRawValue();
    const amountValue = parseMoney(amount);
    const registrationValue = parseMoney(registrationFee);
    let valid = true;
    if (Number.isNaN(amountValue)) {
      editor.form.controls.amount.setErrors({ server: 'Enter the amount as a number, for example 36000.00.' });
      valid = false;
    }
    if (Number.isNaN(registrationValue)) {
      editor.form.controls.registrationFee.setErrors({ server: 'Enter the registration fee as a number, or leave it empty.' });
      valid = false;
    }
    if (standardId === null) {
      editor.form.controls.standardId.setErrors({ server: 'Choose the standard.' });
      valid = false;
    }
    if (!valid) {
      editor.form.markAllAsTouched();
      return null;
    }
    return {
      structureVersion: structure.version,
      standardId: standardId as number,
      categoryId,
      amount: amountValue,
      registrationFee: registrationValue as number | null,
      dueDate: dueDate === '' ? null : dueDate,
    };
  }

  private onSaveFailed(editor: RowEditor, error: unknown): void {
    const yearId = this.yearId();
    if (feeConflictOf(error) instanceof StaleStructure && yearId !== null) {
      // Someone saved or finalised first: show the latest values; the interceptor has said so in a toast.
      this.load(yearId);
      return;
    }
    applyFieldErrors(editor.form, error);
    this.editors.update((rows) => [...rows]);
  }

  private setBusy(editor: RowEditor, busy: boolean): void {
    editor.busy = busy;
    this.editors.update((rows) => [...rows]);
  }
}
