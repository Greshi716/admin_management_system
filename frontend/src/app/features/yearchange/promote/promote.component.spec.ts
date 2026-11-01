import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { ToastService } from '../../../core/toast/toast.service';
import { PromoteScreen, YearChangeService } from '../yearchange.service';
import { PromoteComponent } from './promote.component';

const MOCK_SCREEN: PromoteScreen = {
  fromYear: '2025-26',
  toYear: '2026-27',
  nextStandard: { id: 2, name: 'Standard 2', usesStream: false },
  streamChoices: ['Science', 'Commerce'],
  divisions: [
    { id: 10, name: 'A', standardId: 1 },
    { id: 20, name: 'A', standardId: 2 },
    { id: 21, name: 'B', standardId: 2 },
  ],
  categories: [
    { id: 1, name: 'General' },
    { id: 2, name: 'RTE' },
  ],
  students: [
    {
      studentId: 101,
      enrollmentId: 201,
      grNo: 'GR-101',
      name: 'Alice Smith',
      division: 'A',
      category: 'General',
      unpaidYearFee: '3000.00',
      defaultOutcome: 'PROMOTED',
      defaultDivisionId: 20,
      defaultStream: null,
      version: 1,
    },
    {
      studentId: 102,
      enrollmentId: 202,
      grNo: 'GR-102',
      name: 'Bob Jones',
      division: 'A',
      category: 'General',
      unpaidYearFee: '0.00',
      defaultOutcome: 'PROMOTED',
      defaultDivisionId: 20,
      defaultStream: null,
      version: 1,
    },
  ],
};

const MOCK_STD10_SCREEN: PromoteScreen = {
  ...MOCK_SCREEN,
  nextStandard: { id: 11, name: 'Standard 11', usesStream: true },
};

const MOCK_STD12_SCREEN: PromoteScreen = {
  ...MOCK_SCREEN,
  nextStandard: null,
};

describe('PromoteComponent (FR-006, FR-007, FR-008, FR-009, FR-010, FR-011, FR-012, FR-019)', () => {
  let fixture: ComponentFixture<PromoteComponent>;
  let component: PromoteComponent;
  let yearChangeService: Record<'getPromoteScreen' | 'promote' | 'refreshStatus', ReturnType<typeof vi.fn>>;
  let toast: Record<'success' | 'info' | 'error', ReturnType<typeof vi.fn>>;

  function setup(screen: PromoteScreen = MOCK_SCREEN, standardId = 1): void {
    yearChangeService = {
      getPromoteScreen: vi.fn().mockReturnValue(of(screen)),
      promote: vi.fn().mockReturnValue(
        of({
          runId: 1,
          promoted: 1,
          detained: 1,
          left: 0,
          carriedTotal: '3000.00',
        }),
      ),
      refreshStatus: vi.fn().mockReturnValue(of(null)),
    };

    toast = {
      success: vi.fn(),
      info: vi.fn(),
      error: vi.fn(),
    };

    TestBed.configureTestingModule({
      imports: [PromoteComponent],
      providers: [
        provideRouter([]),
        { provide: YearChangeService, useValue: yearChangeService },
        { provide: ToastService, useValue: toast },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: () => String(standardId),
              },
            },
          },
        },
      ],
    });

    fixture = TestBed.createComponent(PromoteComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('FR-007: loads students and defaults them all ticked for promotion', () => {
    setup(MOCK_SCREEN);
    expect(component['rows']().length).toBe(2);
    expect(component['promotedCount']()).toBe(2);
    expect(component['detainedCount']()).toBe(0);
    expect(component['leftCount']()).toBe(0);
    expect(component['carriedTotal']()).toBe(3000.0);
  });

  it('untick-all and select-all update student ticked status', () => {
    setup(MOCK_SCREEN);
    component.untickAll();
    expect(component['promotedCount']()).toBe(0);
    expect(component['detainedCount']()).toBe(2);

    component.selectAll();
    expect(component['promotedCount']()).toBe(2);
    expect(component['detainedCount']()).toBe(0);
  });

  it('FR-010: toggleLeft marks student Left and unticks them', () => {
    setup(MOCK_SCREEN);
    component.toggleLeft(0);
    expect(component['rows']()[0].isLeft).toBe(true);
    expect(component['promotedCount']()).toBe(1);
    expect(component['leftCount']()).toBe(1);
    // Unpaid fee of Left student is not carried
    expect(component['carriedTotal']()).toBe(0.0);

    // Toggle Left off
    component.toggleLeft(0);
    expect(component['rows']()[0].isLeft).toBe(false);
    expect(component['promotedCount']()).toBe(2);
    expect(component['leftCount']()).toBe(0);
  });

  it('FR-009: Std 12 has no promote tick and defaults students to detained or left', () => {
    setup(MOCK_STD12_SCREEN, 12);
    expect(component['isStd12']()).toBe(true);
    expect(component['promotedCount']()).toBe(0);
    expect(component['detainedCount']()).toBe(2);

    component.toggleLeft(1);
    expect(component['detainedCount']()).toBe(1);
    expect(component['leftCount']()).toBe(1);
  });

  it('FR-012: bulk stream applies chosen stream to all promoted students', () => {
    setup(MOCK_STD10_SCREEN, 10);
    expect(component['targetUsesStream']()).toBe(true);

    component['bulkStream'].set('Commerce');
    component.applyBulkStream();

    expect(component['rows']()[0].stream).toBe('Commerce');
    expect(component['rows']()[1].stream).toBe('Commerce');
    expect(toast.info).toHaveBeenCalledWith('Applied Commerce stream to promoted students.');
  });

  it('FR-006, FR-016: confirmation dialog opens and confirms promotion save', () => {
    setup(MOCK_SCREEN);
    component.toggleTick(1); // untick second student -> Detained

    component.openConfirm();
    expect(component['confirming']()).toBe(true);

    component.confirmAndPromote();

    expect(yearChangeService.promote).toHaveBeenCalledWith(
      1,
      expect.objectContaining({
        lines: [
          expect.objectContaining({
            enrollmentId: 201,
            outcome: 'PROMOTED',
          }),
          expect.objectContaining({
            enrollmentId: 202,
            outcome: 'DETAINED',
          }),
        ],
      }),
    );

    expect(component['result']()).not.toBeNull();
    expect(component['confirming']()).toBe(false);
  });
});
