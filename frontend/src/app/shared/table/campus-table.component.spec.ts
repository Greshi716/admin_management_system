import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { MessageService } from 'primeng/api';
import { Table } from 'primeng/table';
import { ToastService } from '../../core/toast/toast.service';
import { CampusTableComponent } from './campus-table.component';
import { FileDownloader } from './file-downloader';
import { CampusColumn } from './table-types';

const SEARCH = '/api/academic-years/search';
const EXPORT = '/api/academic-years/export';

const columns: CampusColumn[] = [
  { field: 'name', header: 'Name' },
  { field: 'startDate', header: 'Starts', type: 'date' },
  { field: 'current', header: 'Current', type: 'boolean' },
  { field: 'secret', header: 'Secret', hidden: true },
];

const twoRows = {
  rows: [
    { name: '2026-27', startDate: '2026-04-01', current: true, secret: 'x' },
    { name: '2025-26', startDate: '2025-04-01', current: false, secret: 'y' },
  ],
  total: 2,
};

// FR-027, FR-029, FR-030, FR-031, FR-032, FR-033, FR-034, FR-038
describe('CampusTableComponent', () => {
  let fixture: ComponentFixture<CampusTableComponent>;
  let controller: HttpTestingController;
  let downloader: { save: ReturnType<typeof vi.fn> };
  let toast: { success: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    downloader = { save: vi.fn() };
    toast = { success: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        MessageService,
        { provide: FileDownloader, useValue: downloader },
        { provide: ToastService, useValue: toast },
      ],
    });
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => vi.useRealTimers());

  function create(): void {
    fixture = TestBed.createComponent(CampusTableComponent);
    fixture.componentRef.setInput('columns', columns);
    fixture.componentRef.setInput('endpoint', '/api/academic-years');
    fixture.detectChanges();
  }

  /** The newest search request; older ones were cancelled by a newer one. */
  function latestSearch(): TestRequest {
    const all = controller.match(SEARCH);
    expect(all.length).toBeGreaterThan(0);
    return all[all.length - 1];
  }

  function loaded(page = twoRows): void {
    latestSearch().flush(page);
    fixture.detectChanges();
  }

  function table(): Table {
    return fixture.debugElement.query(By.directive(Table)).componentInstance as Table;
  }

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  it('loads the first page of 25 from the search endpoint', () => {
    create();

    const request = latestSearch();
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ first: 0, rows: 25, filters: {} });
    request.flush(twoRows);
  });

  it('shows skeleton rows while loading and the data afterwards', () => {
    create();
    expect(fixture.nativeElement.querySelectorAll('p-skeleton').length).toBeGreaterThan(0);

    loaded();

    expect(fixture.nativeElement.querySelectorAll('p-skeleton').length).toBe(0);
    expect(text()).toContain('2026-27');
    expect(text()).toContain('01-04-2026');
    expect(text()).toContain('Yes');
    expect(text()).toContain('No');
  });

  it('shows the total number of matching rows', () => {
    create();

    loaded({ rows: twoRows.rows, total: 130 });

    expect(text()).toContain('of 130 rows');
  });

  it('shows an empty state when nothing matches', () => {
    create();

    loaded({ rows: [], total: 0 });

    expect(text()).toContain('No rows found');
    expect(fixture.nativeElement.querySelectorAll('p-skeleton').length).toBe(0);
  });

  it('does not show hidden columns', () => {
    create();
    loaded();

    const headers = Array.from(fixture.nativeElement.querySelectorAll('th')).map((th) => (th as HTMLElement).textContent);
    expect(headers.join(' ')).toContain('Name');
    expect(headers.join(' ')).not.toContain('Secret');
    expect(fixture.nativeElement.querySelectorAll('tbody tr:first-child td')).toHaveLength(3);
  });

  it('offers page sizes 10, 25, 50 and 100', () => {
    create();
    loaded();

    expect(table().rowsPerPageOptions()).toEqual([10, 25, 50, 100]);
    expect(table().rows()).toBe(25);
  });

  it('waits 300 ms after typing and sends one search from the first page', () => {
    create();
    loaded();
    vi.useFakeTimers();
    const input = fixture.nativeElement.querySelector('input[type="search"]') as HTMLInputElement;

    for (const typed of ['a', 'ab', 'abc']) {
      input.value = typed;
      input.dispatchEvent(new Event('input'));
      vi.advanceTimersByTime(100);
    }
    expect(controller.match(SEARCH)).toHaveLength(0);

    vi.advanceTimersByTime(300);

    const requests = controller.match(SEARCH);
    expect(requests).toHaveLength(1);
    expect(requests[0].request.body).toEqual({ first: 0, rows: 25, filters: {}, globalFilter: 'abc' });
    requests[0].flush(twoRows);
  });

  it('sorts when a column heading is clicked', () => {
    create();
    loaded();

    (fixture.nativeElement.querySelector('th') as HTMLElement).click();

    expect(latestSearch().request.body).toMatchObject({ sortField: 'name', sortOrder: 1 });
  });

  it('sends the page, size, sort and only the filters that have a value', () => {
    create();
    loaded();

    table().onLazyLoad.emit({
      first: 50,
      rows: 10,
      sortField: 'startDate',
      sortOrder: -1,
      filters: {
        name: { value: 'abc', matchMode: 'contains' },
        startDate: { value: ['2026-01-01', ''], matchMode: 'dateRange' },
        current: { value: null, matchMode: 'equals' },
        secret: { value: '', matchMode: 'contains' },
        other: { value: [' ', ''], matchMode: 'dateRange' },
      },
    });

    expect(latestSearch().request.body).toEqual({
      first: 50,
      rows: 10,
      sortField: 'startDate',
      sortOrder: -1,
      filters: {
        name: { value: 'abc', matchMode: 'contains' },
        startDate: { value: ['2026-01-01', ''], matchMode: 'dateRange' },
      },
    });
  });

  it('keeps the search text when the page changes', () => {
    create();
    loaded();
    vi.useFakeTimers();
    const input = fixture.nativeElement.querySelector('input[type="search"]') as HTMLInputElement;
    input.value = 'abc';
    input.dispatchEvent(new Event('input'));
    vi.advanceTimersByTime(300);
    controller.match(SEARCH).forEach((r) => r.flush(twoRows));

    table().onLazyLoad.emit({ first: 25, rows: 25 });

    expect(latestSearch().request.body).toMatchObject({ first: 25, globalFilter: 'abc' });
  });

  it('exports with the current request and saves the file under the server’s name', () => {
    create();
    loaded();
    const button = fixture.nativeElement.querySelector('p-button button') as HTMLButtonElement;
    expect(button.disabled).toBe(false);

    button.click();

    const request = controller.expectOne(EXPORT);
    expect(request.request.method).toBe('POST');
    expect(request.request.responseType).toBe('blob');
    expect(request.request.body).toMatchObject({ first: 0, rows: 25, filters: {} });
    const file = new Blob(['xlsx']);
    request.flush(file, { headers: { 'Content-Disposition': 'attachment; filename="academic-years-2026-10-25.xlsx"' } });
    expect(downloader.save).toHaveBeenCalledWith(file, 'academic-years-2026-10-25.xlsx');
    expect(toast.success).toHaveBeenCalled();
  });

  it('disables export when there is nothing to export', () => {
    create();
    loaded({ rows: [], total: 0 });

    expect((fixture.nativeElement.querySelector('p-button button') as HTMLButtonElement).disabled).toBe(true);
  });

  it('stops loading and keeps the old rows when a load fails', () => {
    create();
    loaded();

    table().onLazyLoad.emit({ first: 25, rows: 25 });
    latestSearch().flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('p-skeleton').length).toBe(0);
    expect(text()).toContain('2026-27');
  });

  it('does not save anything when the export fails', () => {
    create();
    loaded();
    (fixture.nativeElement.querySelector('p-button button') as HTMLButtonElement).click();

    controller.expectOne(EXPORT).flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(downloader.save).not.toHaveBeenCalled();
    expect((fixture.nativeElement.querySelector('p-button button') as HTMLButtonElement).disabled).toBe(false);
  });
});
