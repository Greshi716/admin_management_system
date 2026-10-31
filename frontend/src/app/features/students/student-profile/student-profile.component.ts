import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { AuthStore } from '../../../core/auth/auth.store';
import { problemOf, STALE_VERSION } from '../../../core/http/api-problem';
import { ToastService } from '../../../core/toast/toast.service';
import { StaleField, StaleVersionDialogComponent } from '../../../shared/forms/stale-version-dialog.component';
import { formatCell } from '../../../shared/table/format-cell';
import { AvatarComponent } from '../../../shared/ui/avatar.component';
import { DialogComponent } from '../../../shared/ui/dialog.component';
import { PageSkeletonComponent } from '../../../shared/ui/page-skeleton.component';
import { ClassRecordDialogComponent } from '../class-record-dialog/class-record-dialog.component';
import { FeesService, StudentFees } from '../../fees/fees.service';
import { formatMoney } from '../../../shared/ui/money';
import { paidUpToText, receiptStatusClass, receiptStatusText } from '../../finance/finance-format';
import { Enrollment, HistoryResult, Student, StudentsService } from '../students.service';
import { historyLines, recordLabel } from './history-lines';

/**
 * The student profile (FR-007, FR-009, FR-019): personal details, contact and admission, the class history and the
 * History block. Aadhaar and religion/caste arrive masked. The Fees block shows the fee, the amount brought forward
 * and (F3) paid, pending, "Paid up to" and the receipts with their status, all computed by the server (FR-014).
 */
@Component({
  selector: 'app-student-profile',
  imports: [RouterLink, AvatarComponent, PageSkeletonComponent, DialogComponent, StaleVersionDialogComponent, ClassRecordDialogComponent],
  templateUrl: './student-profile.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StudentProfileComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly students = inject(StudentsService);
  private readonly feeService = inject(FeesService);
  private readonly toast = inject(ToastService);
  private readonly auth = inject(AuthStore);

  protected readonly loading = signal(true);
  protected readonly failed = signal(false);
  protected readonly student = signal<Student | null>(null);
  protected readonly history = signal<HistoryResult>({ entries: [], truncated: false });
  /** The Fees block: amounts come from the server, the browser adds nothing (F2 FR-009, FR-011). */
  protected readonly fees = signal<StudentFees | null>(null);
  protected readonly feesFailed = signal(false);

  /** The record shown in the heading: the current academic year's, else the most recent one (as in the list). */
  protected readonly current = computed(() => {
    const history = this.student()?.classHistory ?? [];
    const year = this.auth.currentYear();
    return history.find((record) => record.academicYearName === year) ?? history[0] ?? null;
  });
  protected readonly fullName = computed(() => {
    const s = this.student();
    return s ? [s.firstName, s.middleName, s.surname].filter(Boolean).join(' ') : '';
  });

  /** The class record being changed in the dialog, if any. */
  protected readonly changing = signal<Enrollment | null>(null);
  protected readonly confirmingLeft = signal(false);
  protected readonly busy = signal(false);
  protected readonly stale = signal<Record<string, unknown> | null>(null);
  protected readonly staleFields: StaleField[] = [
    { field: 'status', label: 'Status' },
    { field: 'address', label: 'Address' },
    { field: 'mobile1', label: 'Mobile 1' },
  ];

  protected readonly formatCell = formatCell;
  protected readonly money = formatMoney;
  protected readonly paidUpToText = paidUpToText;
  protected readonly receiptStatusClass = receiptStatusClass;
  protected readonly receiptStatusText = receiptStatusText;
  protected readonly historyLines = historyLines;
  protected readonly recordLabel = recordLabel;

  ngOnInit(): void {
    this.load();
  }

  /** Marks the student as left after the confirmation. There is no way back (FR-004). */
  protected markLeft(): void {
    const s = this.student();
    if (!s || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.students.markLeft(s.id, s.version).subscribe({
      next: () => {
        this.busy.set(false);
        this.confirmingLeft.set(false);
        this.toast.success('The student was marked as left.');
        this.load();
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.confirmingLeft.set(false);
        if (error instanceof HttpErrorResponse && problemOf(error.error)?.code === STALE_VERSION) {
          this.stale.set((problemOf(error.error)?.current ?? {}) as Record<string, unknown>);
        }
        // any other reason was shown by the error interceptor
      },
    });
  }

  /** Shows the latest saved record; the Mark as left button comes back only if the student is still Active. */
  protected useLatest(): void {
    this.stale.set(null);
    this.load();
  }

  protected classChanged(): void {
    this.changing.set(null);
    this.load();
  }

  /** The Fees block loads on its own: a failure leaves the rest of the profile as it is (the interceptor says why). */
  private loadFees(studentId: number): void {
    this.feeService.studentFees(studentId).subscribe({
      next: (fees) => {
        this.fees.set(fees);
        this.feesFailed.set(false);
      },
      error: () => {
        this.fees.set(null);
        this.feesFailed.set(true);
      },
    });
  }

  protected load(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    forkJoin({ student: this.students.get(id), history: this.students.history(id) }).subscribe({
      next: ({ student, history }) => {
        this.student.set(student);
        this.history.set(history);
        this.loadFees(student.id);
        this.loading.set(false);
      },
      error: () => {
        // The error interceptor has shown the reason (for example that the student does not exist).
        this.failed.set(true);
        this.loading.set(false);
      },
    });
  }
}
