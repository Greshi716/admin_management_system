import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CampusTableComponent } from '../../../shared/table/campus-table.component';
import { CampusColumn } from '../../../shared/table/table-types';
import { AvatarComponent } from '../../../shared/ui/avatar.component';
import { PageSkeletonComponent } from '../../../shared/ui/page-skeleton.component';
import { forkJoin } from 'rxjs';
import { StudentsService } from '../students.service';
import { formatMoney } from '../../../shared/ui/money';
import { formatDate } from '../../finance/finance-format';
import { feeStatusClass, feeText, paidUpToCell, recordYearNote, studentColumns } from './student-columns';

type StatusScope = 'Active' | 'Left' | 'All';

/**
 * The Students list (FR-008): search by name, GR No. or mobile, filters on Standard, Division and Category, a
 * status scope (Active by default), and Export to Excel, all through the one shared table.
 */
@Component({
  selector: 'app-student-list',
  imports: [RouterLink, CampusTableComponent, AvatarComponent, PageSkeletonComponent],
  templateUrl: './student-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StudentListComponent implements OnInit {
  private readonly students = inject(StudentsService);

  protected readonly endpoint = '/api/students';
  protected readonly statuses: StatusScope[] = ['Active', 'Left', 'All'];
  protected readonly status = signal<StatusScope>('Active');
  protected readonly columns = signal<CampusColumn[] | null>(null);
  private currentYearId: number | null = null;

  ngOnInit(): void {
    forkJoin({ lists: this.students.pickLists(), options: this.students.filterOptions() }).subscribe({
      next: ({ lists, options }) => {
        this.currentYearId = lists.currentYear?.id ?? null;
        this.columns.set(studentColumns(options));
      },
      error: () => this.columns.set(studentColumns(null)), // the error interceptor has shown the reason
    });
  }

  protected chooseStatus(event: Event): void {
    this.status.set((event.target as HTMLSelectElement).value as StatusScope);
  }

  protected yearNote(row: Record<string, unknown>): string | null {
    return recordYearNote(row, this.currentYearId);
  }

  protected feeText(row: Record<string, unknown>): string {
    return feeText(row, formatMoney);
  }

  /** An amount of the fee columns with two decimals; a dash when the student has no fee. */
  protected amount(row: Record<string, unknown>, field: string): string {
    const value = row[field];
    return typeof value === 'number' ? formatMoney(value) : '—';
  }

  protected owing(row: Record<string, unknown>, field: string): boolean {
    const value = row[field];
    return typeof value === 'number' && value > 0;
  }

  protected paidUpTo(row: Record<string, unknown>): string {
    return paidUpToCell(row, formatDate) || '—';
  }

  protected feeStatusClass = feeStatusClass;

  protected id(row: Record<string, unknown>): number {
    return row['id'] as number;
  }

  protected text(row: Record<string, unknown>, field: string): string {
    return String(row[field] ?? '');
  }

  protected photoVersion(row: Record<string, unknown>): number | null {
    const version = row['photoVersion'];
    return typeof version === 'number' ? version : null;
  }
}
