import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { PageSkeletonComponent } from '../../../shared/ui/page-skeleton.component';
import { AdmissionRow, AdmissionsService, ImportResult } from '../admissions.service';
import { UploadDialogComponent } from '../upload-dialog/upload-dialog.component';

@Component({
  selector: 'app-admissions-list',
  imports: [CommonModule, FormsModule, RouterLink, PageSkeletonComponent, UploadDialogComponent],
  templateUrl: './admissions-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdmissionsListComponent implements OnInit {
  private readonly admissionsService = inject(AdmissionsService);

  protected readonly rows = signal<AdmissionRow[]>([]);
  protected readonly total = signal<number>(0);
  protected readonly loading = signal<boolean>(true);
  protected readonly uploadOpen = signal<boolean>(false);

  // Filters and Paging
  protected readonly statusFilter = signal<string>('All');
  protected readonly duplicateOnly = signal<boolean>(false);
  protected readonly search = signal<string>('');
  protected readonly first = signal<number>(0);
  protected readonly pageSize = signal<number>(25);
  protected readonly pageSizes = [10, 25, 50, 100];

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.admissionsService
      .list(
        this.statusFilter(),
        this.duplicateOnly() ? true : undefined,
        this.first(),
        this.pageSize(),
        this.search()
      )
      .subscribe({
        next: (page) => {
          this.rows.set((page.rows || []) as unknown as AdmissionRow[]);
          this.total.set(page.total || 0);
          this.loading.set(false);
        },
        error: () => {
          this.rows.set([]);
          this.total.set(0);
          this.loading.set(false);
        },
      });
  }

  setStatus(status: string): void {
    this.statusFilter.set(status);
    this.first.set(0);
    this.load();
  }

  toggleDuplicates(): void {
    this.duplicateOnly.update((v) => !v);
    this.first.set(0);
    this.load();
  }

  onSearchChange(term: string): void {
    this.search.set(term);
    this.first.set(0);
    this.load();
  }

  onPageSizeChange(newSize: number): void {
    this.pageSize.set(newSize);
    this.first.set(0);
    this.load();
  }

  previousPage(): void {
    const prev = Math.max(0, this.first() - this.pageSize());
    this.first.set(prev);
    this.load();
  }

  nextPage(): void {
    if (this.first() + this.pageSize() < this.total()) {
      this.first.update((f) => f + this.pageSize());
      this.load();
    }
  }

  onUploaded(_result: ImportResult): void {
    this.load();
  }
}
