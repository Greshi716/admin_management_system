import { HttpClient, HttpResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input, model, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Button } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Skeleton } from 'primeng/skeleton';
import { ColumnFilter, SortableColumn, SortIcon, Table, TableLazyLoadEvent } from 'primeng/table';
import { catchError, debounceTime, distinctUntilChanged, EMPTY, of, Subject, switchMap } from 'rxjs';
import { ToastService } from '../../core/toast/toast.service';
import { FileDownloader, fileNameFrom } from './file-downloader';
import { formatCell } from './format-cell';
import {
  CampusColumn,
  DEFAULT_PAGE_SIZE,
  PAGE_SIZES,
  SEARCH_DEBOUNCE_MS,
  TableFilter,
  TablePage,
  TableRequest,
} from './table-types';

/**
 * The one list component for the whole app (FR-034). Give it the columns and the endpoint and it gets server
 * paging with page sizes 10/25/50/100, a debounced search box, per-column filters, sortable headers, skeleton
 * rows while loading, an empty state and Export to Excel (FR-027 to FR-033).
 *
 * `endpoint` is the list's API path, for example `/api/academic-years`; the table posts to `{endpoint}/search`
 * and `{endpoint}/export`.
 */
@Component({
  selector: 'campus-table',
  imports: [Table, SortableColumn, SortIcon, ColumnFilter, Button, InputText, Skeleton],
  templateUrl: './campus-table.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CampusTableComponent {
  readonly columns = input.required<CampusColumn[]>();
  readonly endpoint = input.required<string>();

  protected readonly pageSizes = [...PAGE_SIZES];
  protected readonly first = model<number | null | undefined>(0);
  protected readonly pageSize = signal(DEFAULT_PAGE_SIZE);
  protected readonly search = signal('');
  protected readonly rows = signal<Record<string, unknown>[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(true);
  protected readonly exporting = signal(false);
  protected readonly formatCell = formatCell;

  protected readonly visibleColumns = computed(() => this.columns().filter((column) => !column.hidden));

  /** Placeholders shown instead of rows while a page is loading, so the table keeps its height. */
  protected readonly displayRows = computed<Record<string, unknown>[]>(() =>
    this.loading() ? Array.from({ length: this.pageSize() }, (_, index) => ({ skeleton: index })) : this.rows(),
  );

  private readonly http = inject(HttpClient);
  private readonly toast = inject(ToastService);
  private readonly downloader = inject(FileDownloader);

  private sortField: string | undefined;
  private sortOrder: 1 | -1 | undefined;
  private filters: Record<string, TableFilter> = {};

  private readonly searchTyped = new Subject<string>();
  private readonly loadRequested = new Subject<void>();

  constructor() {
    this.searchTyped
      .pipe(debounceTime(SEARCH_DEBOUNCE_MS), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => {
        this.first.set(0);
        this.loadRequested.next();
      });

    // switchMap drops the answer of an older request when a newer one has been made.
    this.loadRequested
      .pipe(
        switchMap(() => {
          this.loading.set(true);
          return this.http.post<TablePage>(`${this.endpoint()}/search`, this.request()).pipe(
            catchError(() => of(null)), // the error interceptor has already shown the toast
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe((page) => {
        if (page) {
          this.rows.set(page.rows);
          this.total.set(page.total);
        }
        this.loading.set(false);
      });
  }

  /** PrimeNG calls this on first display, page change, page size change, sort and column filter. */
  protected onLazyLoad(event: TableLazyLoadEvent): void {
    this.first.set(event.first ?? 0);
    this.pageSize.set(event.rows ?? DEFAULT_PAGE_SIZE);
    this.sortField = typeof event.sortField === 'string' ? event.sortField : undefined;
    this.sortOrder = this.sortField && (event.sortOrder === 1 || event.sortOrder === -1) ? event.sortOrder : undefined;
    this.filters = this.activeFilters(event.filters);
    this.loadRequested.next();
  }

  protected onSearchInput(event: Event): void {
    const text = (event.target as HTMLInputElement).value;
    this.search.set(text);
    this.searchTyped.next(text.trim());
  }

  protected exportExcel(): void {
    this.exporting.set(true);
    const fallbackName = `${this.endpoint().split('/').filter(Boolean).pop() ?? 'export'}.xlsx`;
    this.http
      .post(`${this.endpoint()}/export`, this.request(), { observe: 'response', responseType: 'blob' })
      .pipe(
        catchError(() => {
          this.exporting.set(false); // the error interceptor has already shown the toast
          return EMPTY;
        }),
      )
      .subscribe((response: HttpResponse<Blob>) => {
        this.exporting.set(false);
        if (response.body) {
          this.downloader.save(
            response.body,
            fileNameFrom(response.headers.get('Content-Disposition'), fallbackName),
          );
          this.toast.success('The Excel file was downloaded.', 'Exported');
        }
      });
  }

  /** Date-range filter: the template calls this with the changed end of the range. */
  protected rangeValue(current: unknown, index: 0 | 1, text: string): [string, string] {
    const pair = Array.isArray(current) ? current : [];
    const range: [string, string] = [String(pair[0] ?? ''), String(pair[1] ?? '')];
    range[index] = text;
    return range;
  }

  protected rangeEnd(current: unknown, index: 0 | 1): string {
    return Array.isArray(current) ? String(current[index] ?? '') : '';
  }

  private request(): TableRequest {
    const request: TableRequest = {
      first: this.first() ?? 0,
      rows: this.pageSize(),
      filters: this.filters,
    };
    if (this.sortField) {
      request.sortField = this.sortField;
      request.sortOrder = this.sortOrder ?? 1;
    }
    const search = this.search().trim();
    if (search) {
      request.globalFilter = search;
    }
    return request;
  }

  /** Keeps only filters that have a value, one per column, in the shape the server reads. */
  private activeFilters(filters: TableLazyLoadEvent['filters']): Record<string, TableFilter> {
    const active: Record<string, TableFilter> = {};
    for (const [field, metadata] of Object.entries(filters ?? {})) {
      const filter = Array.isArray(metadata) ? metadata[0] : metadata;
      const value = filter?.value;
      const blank = (part: unknown) => part === null || part === undefined || String(part).trim() === '';
      const empty = Array.isArray(value) ? value.every(blank) : blank(value);
      if (!empty) {
        active[field] = { value, matchMode: filter?.matchMode ?? 'contains' };
      }
    }
    return active;
  }
}
