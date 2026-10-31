import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ToastService } from '../../../core/toast/toast.service';
import { PageSkeletonComponent } from '../../../shared/ui/page-skeleton.component';
import { formatMoney } from '../../../shared/ui/money';
import {
  CategoryRef,
  DivisionRef,
  PromoteInput,
  PromoteResult,
  PromoteScreen,
  StudentItem,
  YearChangeService,
} from '../yearchange.service';
import { PromoteConfirmDialogComponent } from './promote-confirm-dialog.component';

export interface StudentRowModel {
  student: StudentItem;
  ticked: boolean;
  isLeft: boolean;
  divisionId: number | null;
  categoryId: number | null;
  stream: string | null;
}

@Component({
  selector: 'app-promote',
  imports: [CommonModule, FormsModule, RouterLink, PageSkeletonComponent, PromoteConfirmDialogComponent],
  templateUrl: './promote.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PromoteComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly yearChangeService = inject(YearChangeService);
  private readonly toast = inject(ToastService);

  protected readonly money = formatMoney;

  protected standardId = signal<number>(0);
  protected screen = signal<PromoteScreen | null>(null);
  protected loading = signal(true);
  protected error = signal<string | null>(null);
  protected rows = signal<StudentRowModel[]>([]);
  protected bulkStream = signal<string>('Science');
  protected confirming = signal(false);
  protected busy = signal(false);
  protected result = signal<PromoteResult | null>(null);

  protected readonly isStd12 = computed(() => this.screen()?.nextStandard === null);
  protected readonly targetUsesStream = computed(() => !!this.screen()?.nextStandard?.usesStream);

  protected readonly promotedCount = computed(() => {
    if (this.isStd12()) return 0;
    return this.rows().filter((r) => !r.isLeft && r.ticked).length;
  });

  protected readonly detainedCount = computed(() => {
    if (this.isStd12()) {
      return this.rows().filter((r) => !r.isLeft).length;
    }
    return this.rows().filter((r) => !r.isLeft && !r.ticked).length;
  });

  protected readonly leftCount = computed(() => {
    return this.rows().filter((r) => r.isLeft).length;
  });

  protected readonly carriedTotal = computed(() => {
    return this.rows()
      .filter((r) => !r.isLeft)
      .reduce((sum, r) => sum + (parseFloat(r.student.unpaidYearFee) || 0), 0);
  });

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('standardId'));
    if (!id) {
      this.error.set('No standard selected.');
      this.loading.set(false);
      return;
    }
    this.standardId.set(id);
    this.load(id);
  }

  load(standardId: number): void {
    this.loading.set(true);
    this.error.set(null);
    this.yearChangeService.getPromoteScreen(standardId).subscribe({
      next: (data) => {
        this.screen.set(data);
        const isLast = data.nextStandard === null;

        // Default categories map
        const defaultCatId = data.categories.length > 0 ? data.categories[0].id : null;

        const rowModels: StudentRowModel[] = data.students.map((s) => {
          const matchCat = data.categories.find((c) => c.name.toLowerCase() === s.category.toLowerCase());
          return {
            student: s,
            ticked: !isLast,
            isLeft: false,
            divisionId: s.defaultDivisionId,
            categoryId: matchCat ? matchCat.id : defaultCatId,
            stream: s.defaultStream ?? (data.nextStandard?.usesStream ? 'Science' : null),
          };
        });
        this.rows.set(rowModels);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        const detail = err?.error?.detail || err?.error?.title || 'Failed to load promote screen data.';
        this.error.set(detail);
      },
    });
  }

  selectAll(): void {
    if (this.isStd12()) return;
    this.rows.update((list) =>
      list.map((r) => ({
        ...r,
        ticked: true,
        isLeft: false,
      })),
    );
  }

  untickAll(): void {
    this.rows.update((list) =>
      list.map((r) => ({
        ...r,
        ticked: false,
      })),
    );
  }

  toggleTick(index: number): void {
    if (this.isStd12()) return;
    this.rows.update((list) => {
      const copy = [...list];
      const cur = copy[index];
      copy[index] = { ...cur, ticked: !cur.ticked, isLeft: false };
      return copy;
    });
  }

  toggleLeft(index: number): void {
    this.rows.update((list) => {
      const copy = [...list];
      const cur = copy[index];
      const newLeft = !cur.isLeft;
      copy[index] = {
        ...cur,
        isLeft: newLeft,
        ticked: newLeft ? false : !this.isStd12(),
      };
      return copy;
    });
  }

  applyBulkStream(): void {
    const stream = this.bulkStream();
    this.rows.update((list) =>
      list.map((r) => {
        if (!r.isLeft && r.ticked) {
          return { ...r, stream };
        }
        return r;
      }),
    );
    this.toast.info(`Applied ${stream} stream to promoted students.`);
  }

  divisionsFor(row: StudentRowModel): DivisionRef[] {
    const scr = this.screen();
    if (!scr) return [];
    if (!row.isLeft && row.ticked && scr.nextStandard) {
      return scr.divisions.filter((d) => d.standardId === scr.nextStandard!.id);
    }
    return scr.divisions.filter((d) => d.standardId === this.standardId());
  }

  openConfirm(): void {
    if (this.rows().length === 0) return;
    this.confirming.set(true);
  }

  closeConfirm(): void {
    this.confirming.set(false);
  }

  confirmAndPromote(): void {
    const scr = this.screen();
    if (!scr) return;

    this.busy.set(true);
    const input: PromoteInput = {
      lines: this.rows().map((r) => {
        let outcome: 'PROMOTED' | 'DETAINED' | 'LEFT';
        if (r.isLeft) {
          outcome = 'LEFT';
        } else if (r.ticked && scr.nextStandard) {
          outcome = 'PROMOTED';
        } else {
          outcome = 'DETAINED';
        }

        return {
          enrollmentId: r.student.enrollmentId,
          outcome,
          divisionId: r.isLeft ? null : r.divisionId,
          categoryId: r.isLeft ? null : r.categoryId,
          stream: !r.isLeft && this.targetUsesStream() ? r.stream : r.student.defaultStream ?? null,
          version: r.student.version,
        };
      }),
    };

    this.yearChangeService.promote(this.standardId(), input).subscribe({
      next: (res) => {
        this.busy.set(false);
        this.confirming.set(false);
        this.result.set(res);
        this.yearChangeService.refreshStatus().subscribe({ error: () => {} });
        this.toast.success('Promotion saved successfully.');
      },
      error: (err) => {
        this.busy.set(false);
        const detail = err?.error?.detail || err?.error?.title || 'Promotion could not be saved.';
        this.toast.error(detail);
      },
    });
  }
}
