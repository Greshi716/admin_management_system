import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { YearChangeService, YearChangeStatus } from '../../features/yearchange/yearchange.service';
import { PromoteWarningComponent } from './promote-warning.component';

describe('PromoteWarningComponent (FR-006, FR-014, FR-015)', () => {
  let fixture: ComponentFixture<PromoteWarningComponent>;
  let component: PromoteWarningComponent;
  let yearChangeService: {
    status: ReturnType<typeof vi.fn>;
    refreshStatus: ReturnType<typeof vi.fn>;
  };

  function setup(mockStatus: YearChangeStatus | null): void {
    const statusSignal = vi.fn().mockReturnValue(mockStatus);
    yearChangeService = {
      status: statusSignal as unknown as ReturnType<typeof vi.fn>,
      refreshStatus: vi.fn().mockReturnValue(of(mockStatus)),
    };

    TestBed.configureTestingModule({
      imports: [PromoteWarningComponent],
      providers: [
        provideRouter([]),
        { provide: YearChangeService, useValue: yearChangeService },
      ],
    });

    fixture = TestBed.createComponent(PromoteWarningComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('is absent before the new year starts (active = false)', () => {
    setup({
      active: false,
      newYear: null,
      oldYear: null,
      standards: [],
    });

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="promote-warning-bar"]')).toBeNull();
  });

  it('appears when active and pending standards exist, showing message and link', () => {
    setup({
      active: true,
      newYear: '2026-27',
      oldYear: '2025-26',
      standards: [
        { standardId: 1, name: 'Standard 1', pending: 15 },
        { standardId: 2, name: 'Standard 2', pending: 8 },
      ],
    });

    const el = fixture.nativeElement as HTMLElement;
    const bar = el.querySelector('[data-testid="promote-warning-bar"]');
    expect(bar).not.toBeNull();
    expect(bar?.textContent).toContain('Promotions to the new academic year (2026-27) are not complete.');
    expect(bar?.textContent).toContain('Standard 1 (15 pending)');
    expect(bar?.textContent).toContain('Standard 2 (8 pending)');

    const link = el.querySelector('[data-testid="promote-warning-link"]');
    expect(link).not.toBeNull();
    expect(link?.textContent).toContain('Promote Standard 1');
  });

  it('disappears when all standards are promoted (standards list empty)', () => {
    setup({
      active: true,
      newYear: '2026-27',
      oldYear: '2025-26',
      standards: [],
    });

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="promote-warning-bar"]')).toBeNull();
  });
});
