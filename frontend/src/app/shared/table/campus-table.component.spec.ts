import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
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

describe('CampusTableComponent (FR-029, FR-030, FR-031, FR-032, FR-033, FR-034, FR-038)', () => {
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
    expect(fixture.nativeElement.querySelectorAll('[data-testid="skeleton"]').length).toBeGreaterThan(0);

    loaded();

    expect(fixture.nativeElement.querySelectorAll('[data-testid="skeleton"]').length).toBe(0);
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
    expect(fixture.nativeElement.querySelectorAll('[data-testid="skeleton"]').length).toBe(0);
  });

  it('shows an error row with a retry, not the empty message, when the first load fails', () => {
    create();

    latestSearch().flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(text()).toContain('Could not load the list');
    expect(text()).not.toContain('No rows found');

    (fixture.nativeElement.querySelector('td button') as HTMLButtonElement).click();
    loaded({ rows: twoRows.rows, total: 2 });

    expect(text()).not.toContain('Could not load the list');
    expect(text()).toContain('2026-27');
  });

  it('does not show hidden columns', () => {
    create();
    loaded();

    const headers = Array.from(fixture.nativeElement.querySelectorAll('th')).map((th) => (th as HTMLElement).textContent);
    expect(headers.join(' ')).toContain('Name');
    expect(headers.join(' ')).not.toContain('Secret');
    expect(fixture.nativeElement.querySelectorAll('tbody tr:first-child td')).toHaveLength(3);
  });

  it('offers page sizes 10, 25, 50 and 100, with 25 chosen', () => {
    create();
    loaded();

    const select = fixture.nativeElement.querySelector('select[aria-label="Rows per page"]') as HTMLSelectElement;
    expect(Array.from(select.options).map((option) => option.value)).toEqual(['10', '25', '50', '100']);
    expect(select.value).toBe('25');
  });

  it('asks for the first page again with the new size when the page size changes', () => {
    create();
    loaded({ rows: twoRows.rows, total: 130 });
    const select = fixture.nativeElement.querySelector('select[aria-label="Rows per page"]') as HTMLSelectElement;

    select.value = '50';
    select.dispatchEvent(new Event('change'));

    expect(latestSearch().request.body).toEqual({ first: 0, rows: 50, filters: {} });
  });

  it('moves between pages with the page buttons', () => {
    create();
    loaded({ rows: twoRows.rows, total: 130 });

    (fixture.nativeElement.querySelector('button[aria-label="Next page"]') as HTMLButtonElement).click();
    const nextPage = latestSearch();
    expect(nextPage.request.body).toMatchObject({ first: 25, rows: 25 });
    nextPage.flush({ rows: twoRows.rows, total: 130 });
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('button[aria-label="Last page"]') as HTMLButtonElement).click();
    expect(latestSearch().request.body).toMatchObject({ first: 125 });
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

    (fixture.nativeElement.querySelector('th button') as HTMLElement).click();

    expect(latestSearch().request.body).toMatchObject({ sortField: 'name', sortOrder: 1 });
  });

  it('sends the sort and only the filters that have a value, from the first page', () => {
    create();
    loaded();

    const headings = Array.from(fixture.nativeElement.querySelectorAll('th button:first-child')) as HTMLButtonElement[];
    headings[1].click(); // Starts, ascending
    headings[1].click(); // Starts, descending
    loaded();

    (fixture.nativeElement.querySelector('button[aria-label="Filter Name"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const contains = fixture.nativeElement.querySelector('input[type="text"]') as HTMLInputElement;
    vi.useFakeTimers();
    contains.value = 'abc';
    contains.dispatchEvent(new Event('input'));
    vi.advanceTimersByTime(300);

    expect(latestSearch().request.body).toEqual({
      first: 0,
      rows: 25,
      sortField: 'startDate',
      sortOrder: -1,
      filters: { name: { value: 'abc', matchMode: 'contains' } },
    });
  });

  it('filters a yes/no column at once and removes the filter again when cleared', () => {
    create();
    loaded();
    (fixture.nativeElement.querySelector('button[aria-label="Filter Current"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const choice = fixture.nativeElement.querySelector('select:not([aria-label])') as HTMLSelectElement;

    choice.value = 'true';
    choice.dispatchEvent(new Event('change'));
    const filtered = latestSearch();
    expect(filtered.request.body).toMatchObject({ filters: { current: { value: true, matchMode: 'equals' } } });
    filtered.flush(twoRows);
    fixture.detectChanges();

    const clear = Array.from(fixture.nativeElement.querySelectorAll('button')).find(
      (b) => (b as HTMLElement).textContent?.trim() === 'Clear',
    ) as HTMLButtonElement;
    clear.click();
    expect(latestSearch().request.body).toMatchObject({ filters: {} });
  });

  it('keeps the search text when the page changes', () => {
    create();
    loaded({ rows: twoRows.rows, total: 130 });
    vi.useFakeTimers();
    const input = fixture.nativeElement.querySelector('input[type="search"]') as HTMLInputElement;
    input.value = 'abc';
    input.dispatchEvent(new Event('input'));
    vi.advanceTimersByTime(300);
    controller.match(SEARCH).forEach((r) => r.flush({ rows: twoRows.rows, total: 130 }));

    (fixture.nativeElement.querySelector('button[aria-label="Next page"]') as HTMLButtonElement).click();

    expect(latestSearch().request.body).toMatchObject({ first: 25, globalFilter: 'abc' });
  });

  it('exports with the current request and saves the file under the server’s name', () => {
    create();
    loaded();
    const button = fixture.nativeElement.querySelector('[data-testid="export"]') as HTMLButtonElement;
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

  it('still exports when nothing matches, so the admin gets a file with the headings only', () => {
    create();
    loaded({ rows: [], total: 0 });
    const button = fixture.nativeElement.querySelector('[data-testid="export"]') as HTMLButtonElement;
    expect(button.disabled).toBe(false);

    button.click();

    const request = controller.expectOne(EXPORT);
    const file = new Blob(['xlsx']);
    request.flush(file, { headers: { 'Content-Disposition': 'attachment; filename="academic-years-2026-10-25.xlsx"' } });
    expect(downloader.save).toHaveBeenCalledWith(file, 'academic-years-2026-10-25.xlsx');
  });

  it('disables export only while a page or an export is loading', () => {
    create();
    const button = fixture.nativeElement.querySelector('[data-testid="export"]') as HTMLButtonElement;
    expect(button.disabled).toBe(true); // first page still loading

    loaded();

    expect(button.disabled).toBe(false);
  });

  it('stops loading and keeps the old rows when a load fails', () => {
    create();
    loaded({ rows: twoRows.rows, total: 130 });

    (fixture.nativeElement.querySelector('button[aria-label="Next page"]') as HTMLButtonElement).click();
    latestSearch().flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('[data-testid="skeleton"]').length).toBe(0);
    expect(text()).toContain('2026-27');
  });

  it('sorts ascending on the first click, descending on the second and back to the server order on the third', () => {
    create();
    loaded();
    const heading = () => fixture.nativeElement.querySelector('th button') as HTMLElement;

    heading().click();
    const ascending = latestSearch();
    expect(ascending.request.body).toMatchObject({ sortField: 'name', sortOrder: 1 });
    ascending.flush(twoRows);
    fixture.detectChanges();
    heading().click();
    const descending = latestSearch();
    expect(descending.request.body).toMatchObject({ sortField: 'name', sortOrder: -1 });
    descending.flush(twoRows);
    fixture.detectChanges();
    heading().click();
    const third = latestSearch().request.body as Record<string, unknown>;
    expect(third['sortField']).toBeUndefined();
    expect(third['sortOrder']).toBeUndefined();
  });

  it('marks the sorted column for screen readers', () => {
    create();
    loaded();
    const header = () => fixture.nativeElement.querySelector('th') as HTMLElement;
    expect(header().getAttribute('aria-sort')).toBe('none');

    (header().querySelector('button') as HTMLElement).click();
    loaded();

    expect(header().getAttribute('aria-sort')).toBe('ascending');
  });

  it('exports with the current search, filters and sort', () => {
    create();
    loaded();
    vi.useFakeTimers();
    const input = fixture.nativeElement.querySelector('input[type="search"]') as HTMLInputElement;
    input.value = 'abc';
    input.dispatchEvent(new Event('input'));
    vi.advanceTimersByTime(300);
    loaded();
    (fixture.nativeElement.querySelector('th button') as HTMLElement).click();
    latestSearch().flush(twoRows);
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('button[aria-label="Filter Current"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const choice = fixture.nativeElement.querySelector('select:not([aria-label])') as HTMLSelectElement;
    choice.value = 'true';
    choice.dispatchEvent(new Event('change'));
    latestSearch().flush(twoRows);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[data-testid="export"]') as HTMLButtonElement).click();

    expect(controller.expectOne(EXPORT).request.body).toEqual({
      first: 0,
      rows: 25,
      sortField: 'name',
      sortOrder: 1,
      globalFilter: 'abc',
      filters: { current: { value: true, matchMode: 'equals' } },
    });
  });

  it('sends the extra fields of a list with its own filters, and goes back to the first page when they change', () => {
    fixture = TestBed.createComponent(CampusTableComponent);
    fixture.componentRef.setInput('columns', columns);
    fixture.componentRef.setInput('endpoint', '/api/academic-years');
    fixture.componentRef.setInput('extraParams', { area: 'Fees' });
    fixture.detectChanges();
    const firstLoad = latestSearch();
    expect(firstLoad.request.body).toEqual({ first: 0, rows: 25, filters: {}, area: 'Fees' });
    firstLoad.flush({ rows: twoRows.rows, total: 130 });
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('button[aria-label="Next page"]') as HTMLButtonElement).click();
    latestSearch().flush({ rows: twoRows.rows, total: 130 });
    fixture.detectChanges();

    fixture.componentRef.setInput('extraParams', { area: 'Fees', action: 'Cancel' });
    fixture.detectChanges();

    expect(latestSearch().request.body).toEqual({ first: 0, rows: 25, filters: {}, area: 'Fees', action: 'Cancel' });
  });

  it('shows no rows when the server refuses the query, for example a reversed date range', () => {
    create();
    loaded({ rows: twoRows.rows, total: 130 });

    (fixture.nativeElement.querySelector('button[aria-label="Next page"]') as HTMLButtonElement).click();
    latestSearch().flush({ title: 'Validation failed' }, { status: 422, statusText: 'Unprocessable Entity' });
    fixture.detectChanges();

    expect(text()).toContain('No rows found');
    expect(text()).not.toContain('2026-27');
  });

  it('does not save anything when the export fails', () => {
    create();
    loaded();
    (fixture.nativeElement.querySelector('[data-testid="export"]') as HTMLButtonElement).click();

    controller.expectOne(EXPORT).flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(downloader.save).not.toHaveBeenCalled();
    expect((fixture.nativeElement.querySelector('[data-testid="export"]') as HTMLButtonElement).disabled).toBe(false);
  });

  it('emits the request of every page it loads, for a panel that follows the same filters', () => {
    create();
    const emitted: unknown[] = [];
    fixture.componentInstance.requested.subscribe((request) => emitted.push(request));

    loaded();

    expect(emitted).toEqual([{ first: 0, rows: 25, filters: {} }]);
  });
});

@Component({
  imports: [CampusTableComponent],
  template: `
    <campus-table [columns]="columns" endpoint="/api/things" searchPlaceholder="Name or code">
      <ng-template #cell let-row let-column="column">
        <span data-testid="custom">{{ column.field }}:{{ row['label'] }}</span>
      </ng-template>
    </campus-table>
  `,
})
class HostComponent {
  columns: CampusColumn[] = [
    { field: 'photo', header: 'Photo', custom: true, sortable: false, filterable: false },
    { field: 'label', header: 'Label' },
    {
      field: 'place',
      header: 'Place',
      dropdowns: [
        {
          field: 'standardId',
          label: 'Standard',
          options: [
            { value: 1, label: 'Standard 1' },
            { value: 2, label: 'Standard 2' },
          ],
        },
        { field: 'divisionId', label: 'Division', options: [{ value: 7, label: 'A' }] },
      ],
    },
  ];
}

describe('CampusTableComponent custom cells, dropdown filters and headings (F1 FR-008, FR-015)', () => {
  let fixture: ComponentFixture<HostComponent>;
  let controller: HttpTestingController;
  let element: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: FileDownloader, useValue: { save: vi.fn() } }],
    });
    controller = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(HostComponent);
    element = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  function latest(): TestRequest {
    const all = controller.match('/api/things/search');
    return all[all.length - 1];
  }

  function loaded(): void {
    latest().flush({ rows: [{ label: 'One', place: 'x' }], total: 1 });
    fixture.detectChanges();
  }

  it('draws a custom column through the cell template and the other columns as before', () => {
    loaded();

    expect(element.querySelector('[data-testid="custom"]')?.textContent).toBe('photo:One');
    expect(element.textContent).toContain('One');
    expect(element.querySelectorAll('[data-testid="custom"]').length).toBe(1);
  });

  it('takes the sort arrow and the filter button off a column that is not sortable or filterable', () => {
    loaded();

    expect(element.querySelector('button[aria-label="Filter Photo"]')).toBeNull();
    expect(element.querySelector('button[aria-label="Filter Label"]')).not.toBeNull();
    const headings = Array.from(element.querySelectorAll('th button')).map((b) => b.textContent?.trim());
    expect(headings.some((text) => text?.startsWith('Photo'))).toBe(false);
    expect(headings.some((text) => text?.startsWith('Label'))).toBe(true);
  });

  it('uses the given search hint', () => {
    expect(element.querySelector('input[type="search"]')?.getAttribute('placeholder')).toBe('Name or code');
  });

  it('sends the id chosen in a dropdown filter as an exact match and goes back to the first page', () => {
    loaded();
    (element.querySelector('button[aria-label="Filter Place"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    const select = element.querySelector('[role="group"] select') as HTMLSelectElement;
    select.value = '2';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    const request = latest();
    expect(request.request.body.filters).toEqual({ standardId: { value: 2, matchMode: 'equals' } });
    expect(request.request.body.first).toBe(0);
    request.flush({ rows: [], total: 0 });
  });

  it('keeps two dropdown filters of one column together and clears both', () => {
    loaded();
    (element.querySelector('button[aria-label="Filter Place"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const selects = element.querySelectorAll<HTMLSelectElement>('[role="group"] select');
    selects[0].value = '1';
    selects[0].dispatchEvent(new Event('change'));
    selects[1].value = '7';
    selects[1].dispatchEvent(new Event('change'));
    fixture.detectChanges();

    const both = latest();
    expect(both.request.body.filters).toEqual({
      standardId: { value: 1, matchMode: 'equals' },
      divisionId: { value: 7, matchMode: 'equals' },
    });
    both.flush({ rows: [], total: 0 });
    fixture.detectChanges();
    expect(element.querySelector('button[aria-label="Filter Place"]')?.className).toContain('text-accent-text');

    const clear = Array.from(element.querySelectorAll('[role="group"] button')).find((b) => b.textContent === 'Clear');
    (clear as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(latest().request.body.filters).toEqual({});
  });
});
