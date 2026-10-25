import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { formatCell } from '../../../shared/table/format-cell';
import { AvatarComponent } from '../../../shared/ui/avatar.component';
import { PageSkeletonComponent } from '../../../shared/ui/page-skeleton.component';
import { HistoryResult, Student, StudentsService } from '../students.service';
import { historyLines, recordLabel } from './history-lines';

/**
 * The student profile (FR-007, FR-009, FR-019): personal details, contact and admission, the class history and the
 * History block. Aadhaar and religion/caste arrive masked. There are no fee, receipt or roll number items here:
 * fees belong to a later feature.
 */
@Component({
  selector: 'app-student-profile',
  imports: [RouterLink, AvatarComponent, PageSkeletonComponent],
  templateUrl: './student-profile.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StudentProfileComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly students = inject(StudentsService);

  protected readonly loading = signal(true);
  protected readonly failed = signal(false);
  protected readonly student = signal<Student | null>(null);
  protected readonly history = signal<HistoryResult>({ entries: [], truncated: false });

  /** The newest class record: the yearly values shown in the heading. */
  protected readonly current = computed(() => this.student()?.classHistory[0] ?? null);
  protected readonly fullName = computed(() => {
    const s = this.student();
    return s ? [s.firstName, s.middleName, s.surname].filter(Boolean).join(' ') : '';
  });

  protected readonly formatCell = formatCell;
  protected readonly historyLines = historyLines;
  protected readonly recordLabel = recordLabel;

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    forkJoin({ student: this.students.get(id), history: this.students.history(id) }).subscribe({
      next: ({ student, history }) => {
        this.student.set(student);
        this.history.set(history);
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
