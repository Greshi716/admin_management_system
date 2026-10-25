import { HttpClient, HttpResponse } from '@angular/common/http';
import { NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  contentChild,
  effect,
  inject,
  input,
  OnInit,
  signal,
  TemplateRef,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
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
 * rows while loading, an empty state and Export to Excel (FR-029 to FR-034, FR-038).
 *
 * `endpoint` is the list's API path, for example `/api/academic-years`; the table posts to `{endpoint}/search`
 * and `{endpoint}/export`.
 */
@Component({
  selector: 'campus-table',
  imports: [NgTemplateOutlet],
  templateUrl: './campus-table.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CampusTableComponent implements OnInit {
  readonly columns = input.required<CampusColumn[]>();
  readonly endpoint = input.required<string>();
  /**
   * Extra fields sent with every search and export, for a list that has its own filters (the audit log's date range,
   * area and action). When they change, the table goes back to the first page and loads again.
   */
  readonly extraParams = input<Record<string, unknown>>({});
  /** Optional per-row buttons: give the table an ng-template named rowActions; it receives the row. */
  protected readonly rowActions = contentChild<TemplateRef<{ $implicit: Record<string, unknown> }>>('rowActions');

  protected readonly pageSizes = [...PAGE_SIZES];
  protected readonly first = signal(0);
  protected readonly pageSize = signal(DEFAULT_PAGE_SIZE);
  protected readonly search = signal('');
  protected readonly rows = signal<Record<string, unknown>[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(true);

  /** True when the last load failed for a reason other than a refused query; shows the error row, not "No rows". */
  protected readonly loadFailed = signal(false);
  protected readonly exporting = signal(false);
  protected readonly sortField = signal<string | undefined>(undefined);
  protected readonly sortOrder = signal<1 | -1>(1);
  protected readonly filters = signal<Record<string, TableFilter>>({});
  /** Field of the column whose filter panel is open, if any. */
  protected readonly openFilter = signal<string | null>(null);
  protected readonly formatCell = formatCell;

  protected readonly visibleColumns = computed(() => this.columns().filter((column) => !column.hidden));

  /** Placeholders shown instead of rows while a page is loading, so the table keeps its height. */
  protected readonly displayRows = computed<Record<string, unknown>[]>(() =>
    this.loading() ? Array.from({ length: this.pageSize() }, (_, index) => ({ skeleton: index })) : this.rows(),
  );

  protected readonly lastShown = computed(() => Math.min(this.first() + this.pageSize(), this.total()));
  protected readonly firstShown = computed(() => (this.total() === 0 ? 0 : this.first() + 1));
  protected readonly lastPageStart = computed(() =>
    Math.max(0, Math.floor((this.total() - 1) / this.pageSize()) * this.pageSize()),
  );
  protected readonly pageNumber = computed(() => Math.floor(this.first() / this.pageSize()) + 1);
  protected readonly pageCount = computed(() => Math.max(1, Math.ceil(this.total() / this.pageSize())));

  private readonly http = inject(HttpClient);
  private readonly toast = inject(ToastService);
  private readonly downloader = inject(FileDownloader);

  private readonly searchTyped = new Subject<string>();
  private readonly filterTyped = new Subject<void>();
  private readonly loadRequested = new Subject<void>();
  private seenExtra = '';

  constructor() {
    this.searchTyped
      .pipe(debounceTime(SEARCH_DEBOUNCE_MS), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => {
        this.first.set(0);
        this.loadRequested.next();
      });

    this.filterTyped.pipe(debounceTime(SEARCH_DEBOUNCE_MS), takeUntilDestroyed()).subscribe(() => {
      this.first.set(0);
      this.loadRequested.next();
    });

    // switchMap drops the answer of an older request when a newer one has been made.
    this.loadRequested
      .pipe(
        switchMap(() => {
          this.loading.set(true);
          return this.http.post<TablePage>(`${this.endpoint()}/search`, this.request()).pipe(
            // The error interceptor has already shown the toast. A refused query (422, for example a reversed date
            // range) has no valid result, so it shows no rows; any other failure keeps the rows on screen.
            catchError((error: unknown) =>
              of(error instanceof HttpErrorResponse && error.status === 422 ? { rows: [], total: 0 } : null),
            ),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe((page) => {
        this.loadFailed.set(!page);
        if (page) {
          this.rows.set(page.rows);
          this.total.set(page.total);
        }
        this.loading.set(false);
      });

    effect(() => {
      const extra = JSON.stringify(this.extraParams());
      untracked(() => {
        if (extra !== this.seenExtra) {
          this.seenExtra = extra;
          this.first.set(0);
          this.loadRequested.next();
        }
      });
    });
  }

  ngOnInit(): void {
    this.seenExtra = JSON.stringify(this.extraParams());
    this.loadRequested.next(); // first page; the inputs are set by now
  }

  /** Loads the current page again, for example after a row was added, changed or deleted. */
  reload(): void {
    this.loadRequested.next();
  }

  protected goTo(first: number): void {
    this.first.set(Math.max(0, first));
    this.loadRequested.next();
  }

  protected changePageSize(event: Event): void {
    this.pageSize.set(Number((event.target as HTMLSelectElement).value));
    this.first.set(0);
    this.loadRequested.next();
  }

  /** Clicking a heading sorts ascending, then descending, then back to the server's own order. */
  protected sortBy(field: string): void {
    if (this.sortField() !== field) {
      this.sortField.set(field);
      this.sortOrder.set(1);
    } else if (this.sortOrder() === 1) {
      this.sortOrder.set(-1);
    } else {
      this.sortField.set(undefined);
      this.sortOrder.set(1);
    }
    this.first.set(0);
    this.loadRequested.next();
  }

  protected ariaSort(field: string): 'ascending' | 'descending' | 'none' {
    if (this.sortField() !== field) {
      return 'none';
    }
    return this.sortOrder() === 1 ? 'ascending' : 'descending';
  }

  protected onSearchInput(event: Event): void {
    const text = (event.target as HTMLInputElement).value;
    this.search.set(text);
    this.searchTyped.next(text.trim());
  }

  protected toggleFilter(field: string): void {
    this.openFilter.update((open) => (open === field ? null : field));
  }

  protected filterActive(field: string): boolean {
    return field in this.filters();
  }

  /** The text, number or yes/no value of a column's filter, for showing in its panel. */
  protected filterValue(field: string): string {
    const value = this.filters()[field]?.value;
    return value === undefined || value === null ? '' : String(value);
  }

  /** One end of a date range filter: 0 is "from", 1 is "to". */
  protected rangeEnd(field: string, index: 0 | 1): string {
    const value = this.filters()[field]?.value;
    return Array.isArray(value) ? String(value[index] ?? '') : '';
  }

  protected setFilter(column: CampusColumn, event: Event, immediately = false): void {
    const raw = (event.target as HTMLInputElement | HTMLSelectElement).value;
    let value: unknown = raw;
    if (column.type === 'boolean') {
      value = raw === '' ? null : raw === 'true';
    } else if (column.type === 'number') {
      value = raw === '' ? null : Number(raw);
    }
    this.applyFilter(column, value, column.type === 'boolean' || immediately);
  }

  protected setRange(column: CampusColumn, index: 0 | 1, event: Event): void {
    const text = (event.target as HTMLInputElement).value;
    const current = this.filters()[column.field]?.value;
    const pair = Array.isArray(current) ? current : [];
    const range: [string, string] = [String(pair[0] ?? ''), String(pair[1] ?? '')];
    range[index] = text;
    this.applyFilter(column, range, true);
  }

  protected clearFilter(column: CampusColumn): void {
    this.applyFilter(column, null, true);
    this.openFilter.set(null);
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

  /** Stores the filter of one column, or removes it when it has no value, and reloads from the first page. */
  private applyFilter(column: CampusColumn, value: unknown, immediately: boolean): void {
    const blank = (part: unknown) => part === null || part === undefined || String(part).trim() === '';
    const empty = Array.isArray(value) ? value.every(blank) : blank(value);
    this.filters.update((all) => {
      const next = { ...all };
      if (empty) {
        delete next[column.field];
      } else {
        next[column.field] = { value, matchMode: matchModeOf(column) };
      }
      return next;
    });
    if (immediately) {
      this.first.set(0);
      this.loadRequested.next();
    } else {
      this.filterTyped.next();
    }
  }

  private request(): TableRequest {
    const request: TableRequest = {
      ...this.extraParams(),
      first: this.first(),
      rows: this.pageSize(),
      filters: this.filters(),
    };
    const sortField = this.sortField();
    if (sortField) {
      request.sortField = sortField;
      request.sortOrder = this.sortOrder();
    }
    const search = this.search().trim();
    if (search) {
      request.globalFilter = search;
    }
    return request;
  }
}

function matchModeOf(column: CampusColumn): string {
  switch (column.type) {
    case 'date':
      return 'dateRange';
    case 'boolean':
    case 'number':
      return 'equals';
    default:
      return 'contains';
  }
}
