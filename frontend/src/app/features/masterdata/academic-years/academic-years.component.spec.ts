import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AuthStore } from '../../../core/auth/auth.store';
import { ToastService } from '../../../core/toast/toast.service';
import { FileDownloader } from '../../../shared/table/file-downloader';
import { AcademicYearsComponent } from './academic-years.component';

describe('AcademicYearsComponent year copy (FR-001, FR-003, FR-004)', () => {
  let fixture: ComponentFixture<AcademicYearsComponent>;
  let component: AcademicYearsComponent;
  let controller: HttpTestingController;
  let toast: { success: ReturnType<typeof vi.fn>; error: ReturnType<typeof vi.fn> };
  let store: { noCurrentYear: ReturnType<typeof vi.fn>; refresh: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    toast = { success: vi.fn(), error: vi.fn() };
    store = { noCurrentYear: vi.fn().mockReturnValue(false), refresh: vi.fn().mockReturnValue(of(null)) };

    TestBed.configureTestingModule({
      imports: [AcademicYearsComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ToastService, useValue: toast },
        { provide: AuthStore, useValue: store },
        { provide: FileDownloader, useValue: { save: vi.fn() } },
      ],
    });

    controller = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AcademicYearsComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    controller.verify();
  });

  function initComponent(years: unknown[] = []): void {
    fixture.detectChanges();
    const reqYears = controller.expectOne('/api/academic-years');
    reqYears.flush(years);
    const reqSearch = controller.expectOne('/api/academic-years/search');
    reqSearch.flush({ rows: years, total: years.length });
    fixture.detectChanges();
  }

  it('hides "Copy from last year" checkbox when no previous year exists', () => {
    initComponent([]);

    // Open add dialog
    (component as any).add();
    const reqAdd = controller.expectOne('/api/academic-years');
    reqAdd.flush([]);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const checkbox = element.querySelector('#copyFromLast');
    expect(checkbox).toBeNull();
  });

  it('shows "Copy from last year" checkbox when a previous year exists', () => {
    const existingYear = { id: 1, name: '2025-26', startDate: '2025-04-01', endDate: '2026-03-31' };
    initComponent([existingYear]);

    (component as any).add();
    const reqAdd = controller.expectOne('/api/academic-years');
    reqAdd.flush([existingYear]);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const checkbox = element.querySelector('#copyFromLast');
    expect(checkbox).not.toBeNull();
  });

  it('saving with copyFromLast sends the flag and displays copy result banner with link to fees', () => {
    const existingYear = { id: 1, name: '2025-26', startDate: '2025-04-01', endDate: '2026-03-31' };
    initComponent([existingYear]);

    (component as any).add();
    const reqAdd = controller.expectOne('/api/academic-years');
    reqAdd.flush([existingYear]);
    fixture.detectChanges();

    component['form'].setValue({
      name: '2026-27',
      startDate: '2026-04-01',
      endDate: '2027-03-31',
      copyFromLast: true,
    });

    (component as any).save();

    const saveReq = controller.expectOne('/api/academic-years');
    expect(saveReq.request.method).toBe('POST');
    expect(saveReq.request.body).toEqual({
      name: '2026-27',
      startDate: '2026-04-01',
      endDate: '2027-03-31',
      copyFromLast: true,
    });

    saveReq.flush({
      id: 2,
      copiedFromYearId: 1,
      divisionsCopied: 4,
      feeRowsCopied: 12,
    });

    // Following save: table reload and refresh
    const reqReload = controller.expectOne('/api/academic-years/search');
    reqReload.flush({ rows: [existingYear], total: 1 });
    const reqRefresh = controller.expectOne('/api/academic-years');
    reqRefresh.flush([existingYear]);

    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const banner = element.querySelector('[data-testid="copy-result"]');
    expect(banner).not.toBeNull();
    expect(banner?.textContent).toContain('Copied 4 divisions and 12 fee amounts');
    expect(banner?.querySelector('a')?.getAttribute('href')).toBe('/setup/fees');

    // Dismiss banner
    const dismissBtn = banner?.querySelector('button') as HTMLButtonElement;
    dismissBtn.click();
    fixture.detectChanges();

    expect(element.querySelector('[data-testid="copy-result"]')).toBeNull();
  });
});
