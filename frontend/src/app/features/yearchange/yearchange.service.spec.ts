import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  PromoteInput,
  PromoteResult,
  PromoteScreen,
  YearChangeService,
  YearChangeStatus,
} from './yearchange.service';

describe('YearChangeService (FR-006 to FR-012, FR-014, FR-016)', () => {
  let service: YearChangeService;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(YearChangeService);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  it('getStatus calls GET /api/year-change/status', () => {
    const mockStatus: YearChangeStatus = {
      active: true,
      newYear: '2026-27',
      oldYear: '2025-26',
      standards: [{ standardId: 1, name: 'Std 1', pending: 5 }],
    };

    service.getStatus().subscribe((res) => {
      expect(res).toEqual(mockStatus);
    });

    const req = controller.expectOne('/api/year-change/status');
    expect(req.request.method).toBe('GET');
    req.flush(mockStatus);
  });

  it('getPromoteScreen calls GET /api/year-change/standards/{id}/students', () => {
    const mockScreen: PromoteScreen = {
      fromYear: '2025-26',
      toYear: '2026-27',
      nextStandard: { id: 2, name: 'Std 2', usesStream: false },
      streamChoices: ['Science', 'Commerce'],
      divisions: [{ id: 11, name: 'A', standardId: 2 }],
      categories: [{ id: 1, name: 'General' }],
      students: [
        {
          studentId: 101,
          enrollmentId: 201,
          grNo: 'GR-101',
          name: 'Student One',
          division: 'A',
          category: 'General',
          unpaidYearFee: '3000.00',
          defaultOutcome: 'PROMOTED',
          defaultDivisionId: 11,
          defaultStream: null,
          version: 1,
        },
      ],
    };

    service.getPromoteScreen(1).subscribe((res) => {
      expect(res).toEqual(mockScreen);
    });

    const req = controller.expectOne('/api/year-change/standards/1/students');
    expect(req.request.method).toBe('GET');
    req.flush(mockScreen);
  });

  it('promote calls POST /api/year-change/standards/{id}/promote', () => {
    const input: PromoteInput = {
      lines: [
        {
          enrollmentId: 201,
          outcome: 'PROMOTED',
          divisionId: 11,
          categoryId: 1,
          stream: null,
          version: 1,
        },
      ],
    };

    const mockResult: PromoteResult = {
      runId: 99,
      promoted: 1,
      detained: 0,
      left: 0,
      carriedTotal: '3000.00',
    };

    service.promote(1, input).subscribe((res) => {
      expect(res).toEqual(mockResult);
    });

    const req = controller.expectOne('/api/year-change/standards/1/promote');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(input);
    req.flush(mockResult);
  });
});
