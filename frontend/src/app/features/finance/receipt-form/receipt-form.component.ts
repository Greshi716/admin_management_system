import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, EMPTY, merge, of, Subject, switchMap } from 'rxjs';
import { ToastService } from '../../../core/toast/toast.service';
import { applyFieldErrors, serverMessage } from '../../../shared/forms/api-error';
import { formatMoney, parseMoney } from '../../../shared/ui/money';
import { PageSkeletonComponent } from '../../../shared/ui/page-skeleton.component';
import { BalanceChangedDialogComponent } from '../balance-changed-dialog/balance-changed-dialog.component';
import { formatDate, paidUpToText, todayIso } from '../finance-format';
import { ReceiptConfirmDetails, ReceiptConfirmDialogComponent } from '../receipt-confirm-dialog/receipt-confirm-dialog.component';
import {
  BalanceChanged,
  BroughtForwardPending,
  CurrentYearState,
  FeeCollection,
  ReceiptInput,
  ReceiptPreview,
  ReceiptRefusal,
  receiptConflictOf,
  ReceiptsService,
} from '../receipts.service';

interface StudentHit {
  id: number;
  grNo: string;
  name: string;
  stdDiv: string;
}

/** Why a current-year receipt is not possible, in words for the admin (FR-007). */
const STATE_TEXT: Record<Exclude<CurrentYearState, 'FEE_SET'>, string> = {
  FEE_NOT_SET: "Fee not set for this student's standard and category.",
  FEE_NOT_FINALISED: "The fee structure for this year is not finalised yet.",
  NO_CLASS_RECORD: 'This student has no class record in the current academic year.',
  NO_CURRENT_YEAR: 'Today falls in no academic year.',
};

/**
 * The Collect fee screen (FR-001 to FR-008, FR-016): find the student, type the receipt no. from the book, the date
 * and the amounts, see the total and the balance after (both from the server, the browser adds no money), then
 * Save opens the confirmation dialog. Refusals show beside the field they belong to. There is no cancel link and no
 * edit: a saved receipt is final (FR-009).
 */
@Component({
  selector: 'app-receipt-form',
  imports: [ReactiveFormsModule, PageSkeletonComponent, ReceiptConfirmDialogComponent, BalanceChangedDialogComponent],
  templateUrl: './receipt-form.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReceiptFormComponent {
  private readonly fb = inject(FormBuilder).nonNullable;
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly receipts = inject(ReceiptsService);
  private readonly toast = inject(ToastService);

  protected readonly money = formatMoney;
  protected readonly date = formatDate;
  protected readonly paidUpTo = paidUpToText;
  protected readonly serverMessage = serverMessage;

  protected readonly search = this.fb.control('');
  protected readonly hits = signal<StudentHit[]>([]);
  protected readonly ctx = signal<FeeCollection | null>(null);
  protected readonly loading = signal(false);
  protected readonly failed = signal(false);
  protected readonly preview = signal<ReceiptPreview | null>(null);
  /** A refusal that belongs to no single field (earlier years first, fee not set). */
  protected readonly note = signal<string | null>(null);
  protected readonly confirming = signal<ReceiptConfirmDetails | null>(null);
  protected readonly busy = signal(false);
  /** The fresh context of a BALANCE_CHANGED answer; the pop-up shows while it is set. */
  protected readonly stale = signal<FeeCollection | null>(null);

  protected readonly form = this.fb.group({
    receiptType: this.fb.control<'CURRENT_YEAR' | 'PREVIOUS_YEARS'>('CURRENT_YEAR'),
    previousYearId: this.fb.control(''),
    receiptNo: this.fb.control(''),
    receiptDate: this.fb.control(todayIso()),
    registrationPaid: this.fb.control(''),
    feePaid: this.fb.control(''),
    paymentMode: this.fb.control<'CASH' | 'CHEQUE'>('CASH'),
    chequeNo: this.fb.control(''),
    chequeDate: this.fb.control(''),
    bankName: this.fb.control(''),
  });

  protected readonly type = toSignal(this.form.controls.receiptType.valueChanges, { initialValue: 'CURRENT_YEAR' as const });
  protected readonly mode = toSignal(this.form.controls.paymentMode.valueChanges, { initialValue: 'CASH' as const });

  /** The current-year rows are open only when the fee is set and no earlier year is pending. */
  protected readonly stateText = computed(() => {
    const state = this.ctx()?.currentYear?.state;
    return state && state !== 'FEE_SET' ? STATE_TEXT[state] : null;
  });
  protected readonly strip = computed(() => this.ctx()?.balance ?? null);

  private readonly previewTrigger = new Subject<void>();

  constructor() {
    this.route.queryParamMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      const id = Number(params.get('studentId'));
      if (id > 0) {
        this.load(id);
      } else {
        this.ctx.set(null);
      }
    });

    this.search.valueChanges
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        switchMap((text) => (text.trim().length < 2 ? of(null) : this.receipts.findStudents(text.trim()).pipe(catchError(() => of(null))))),
        takeUntilDestroyed(),
      )
      .subscribe((page) => this.hits.set((page?.rows ?? []) as unknown as StudentHit[]));

    merge(this.form.controls.registrationPaid.valueChanges, this.form.controls.feePaid.valueChanges, this.form.controls.receiptType.valueChanges, this.form.controls.previousYearId.valueChanges, this.previewTrigger)
      .pipe(
        debounceTime(300),
        switchMap(() => {
          const body = this.previewBody();
          const ctx = this.ctx();
          if (!body || !ctx) {
            this.preview.set(null);
            return EMPTY;
          }
          return this.receipts.preview(ctx.student.id, body).pipe(
            catchError((error) => {
              this.preview.set(null);
              this.refused(error);
              return EMPTY;
            }),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe({ next: (preview) => this.preview.set(preview) });

  }

  /** The link of the order message: switch to the previous-years receipt. */
  protected usePreviousYears(): void {
    this.form.controls.receiptType.setValue('PREVIOUS_YEARS');
    this.typeChanged();
  }

  /** The admin chose the other receipt type: the rows differ, so the typed amounts are cleared. */
  protected typeChanged(): void {
    this.form.controls.registrationPaid.setValue('');
    this.form.controls.feePaid.setValue('');
    this.note.set(null);
  }

  protected pick(hit: StudentHit): void {
    this.search.setValue('', { emitEvent: false });
    this.hits.set([]);
    void this.router.navigate([], { queryParams: { studentId: hit.id } });
  }

  private load(studentId: number): void {
    this.loading.set(true);
    this.failed.set(false);
    this.note.set(null);
    this.preview.set(null);
    this.receipts.feeCollection(studentId).subscribe({
      next: (ctx) => {
        this.loading.set(false);
        this.applyContext(ctx);
      },
      error: () => {
        this.loading.set(false);
        this.failed.set(true);
      },
    });
  }

  private applyContext(ctx: FeeCollection): void {
    this.ctx.set(ctx);
    // Earlier years first when the current year cannot be paid (quickstart 9).
    const type = !ctx.canPayCurrentYear && ctx.canPayPreviousYears ? 'PREVIOUS_YEARS' : this.form.controls.receiptType.value;
    if (type !== this.form.controls.receiptType.value) {
      this.form.controls.receiptType.setValue(type);
    }
    if (ctx.earlierYears.length > 0 && !this.form.controls.previousYearId.value) {
      this.form.controls.previousYearId.setValue(String(ctx.earlierYears[0].id), { emitEvent: false });
    }
    this.previewTrigger.next();
  }

  /** The typed amount: null when blank, NaN when it is not a plain number. The browser never rounds or adds. */
  private amount(name: 'registrationPaid' | 'feePaid'): number | null {
    return parseMoney(this.form.controls[name].value);
  }

  protected amountProblem(name: 'registrationPaid' | 'feePaid'): string | null {
    return Number.isNaN(this.amount(name)) ? 'Enter the amount in digits, for example 1500.50.' : serverMessage(this.form.controls[name]);
  }

  private previewBody(): Partial<ReceiptInput> | null {
    const ctx = this.ctx();
    if (!ctx) {
      return null;
    }
    const registration = this.amount('registrationPaid');
    const fee = this.amount('feePaid');
    if (Number.isNaN(registration) || Number.isNaN(fee) || ((registration ?? 0) === 0 && (fee ?? 0) === 0)) {
      return null;
    }
    const type = this.form.controls.receiptType.value;
    return {
      studentId: ctx.student.id,
      receiptType: type,
      registrationPaid: type === 'CURRENT_YEAR' ? registration : null,
      feePaid: fee,
      previousYearId: type === 'PREVIOUS_YEARS' ? Number(this.form.controls.previousYearId.value) || null : null,
      paymentMode: this.form.controls.paymentMode.value,
    };
  }

  private input(ctx: FeeCollection): ReceiptInput {
    const f = this.form.getRawValue();
    const cheque = f.paymentMode === 'CHEQUE';
    return {
      studentId: ctx.student.id,
      receiptType: f.receiptType,
      receiptNo: f.receiptNo,
      receiptDate: f.receiptDate,
      registrationPaid: f.receiptType === 'CURRENT_YEAR' ? this.amount('registrationPaid') : null,
      feePaid: this.amount('feePaid'),
      previousYearId: f.receiptType === 'PREVIOUS_YEARS' ? Number(f.previousYearId) || null : null,
      paymentMode: f.paymentMode,
      chequeNo: cheque ? f.chequeNo : null,
      chequeDate: cheque ? f.chequeDate || null : null,
      bankName: cheque ? f.bankName : null,
      balanceVersion: ctx.balanceVersion,
    };
  }

  /** Save: the preview is the server's check of the amounts; then the admin confirms what will be written. */
  protected save(): void {
    const ctx = this.ctx();
    if (!ctx || this.busy()) {
      return;
    }
    this.note.set(null);
    if (Number.isNaN(this.amount('registrationPaid')) || Number.isNaN(this.amount('feePaid'))) {
      this.form.markAllAsTouched();
      return;
    }
    const body = this.input(ctx);
    this.busy.set(true);
    this.receipts.preview(ctx.student.id, body, true).subscribe({
      next: (preview) => {
        this.busy.set(false);
        this.preview.set(preview);
        this.confirming.set(this.details(ctx, preview));
      },
      error: (error) => {
        this.busy.set(false);
        this.refused(error);
      },
    });
  }

  private details(ctx: FeeCollection, preview: ReceiptPreview): ReceiptConfirmDetails {
    const f = this.form.getRawValue();
    const lines: { label: string; amount: number }[] = [];
    if (f.receiptType === 'CURRENT_YEAR') {
      if (ctx.chargesRegistrationFee) {
        lines.push({ label: 'Registration fee', amount: preview.registration });
      }
      lines.push({ label: 'Year fee', amount: preview.fee });
    } else {
      lines.push({ label: "Earlier years' fee", amount: preview.fee });
    }
    const previous = f.receiptType === 'PREVIOUS_YEARS';
    const cheque = f.paymentMode === 'CHEQUE';
    return {
      typeLabel: previous ? 'Previous years' : 'Current year',
      yearName: previous ? (ctx.earlierYears.find((year) => String(year.id) === f.previousYearId)?.name ?? null) : null,
      studentName: ctx.student.name,
      grNo: ctx.student.grNo,
      stdDiv: ctx.student.stdDiv,
      receiptNo: f.receiptNo.trim(),
      receiptDate: f.receiptDate,
      lines,
      total: preview.total,
      totalWords: preview.totalWords,
      mode: cheque ? 'Cheque' : 'Cash',
      chequeNo: cheque ? f.chequeNo : null,
      chequeDate: cheque ? f.chequeDate : null,
      bankName: cheque ? f.bankName : null,
    };
  }

  protected confirm(): void {
    const ctx = this.ctx();
    if (!ctx || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.receipts.create(this.input(ctx)).subscribe({
      next: (receipt) => {
        this.busy.set(false);
        this.confirming.set(null);
        this.toast.success(`Receipt ${receipt.receiptNo} saved.`);
        void this.router.navigate(['/finance/receipts', receipt.serialNo]);
      },
      error: (error) => {
        this.busy.set(false);
        this.confirming.set(null);
        this.refused(error);
      },
    });
  }

  protected cancelConfirm(): void {
    this.confirming.set(null);
  }

  /** Puts a refusal where it belongs: next to its field, in the note, or in the balance-changed pop-up. */
  private refused(error: unknown): void {
    const conflict = receiptConflictOf(error);
    if (conflict instanceof BalanceChanged) {
      this.stale.set(conflict.current);
      return;
    }
    const unmatched = applyFieldErrors(this.form, error);
    if (conflict instanceof BroughtForwardPending || conflict instanceof ReceiptRefusal) {
      this.note.set(conflict.message);
    } else if (unmatched.length > 0) {
      this.note.set(unmatched.map((m) => m.message).join(' '));
    }
  }

  /** Refresh in the balance-changed pop-up: take the balance the server has now; what was typed stays. */
  protected refresh(): void {
    const latest = this.stale();
    this.stale.set(null);
    this.confirming.set(null);
    if (latest) {
      this.applyContext(latest);
    }
  }
}
