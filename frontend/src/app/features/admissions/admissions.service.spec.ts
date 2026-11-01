import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AdmissionsService } from './admissions.service';

describe('AdmissionsService', () => {
  let service: AdmissionsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AdmissionsService);
    httpMock = TestBed.inject(HttpTestingController);

    // Initial pending-count call in constructor
    const req = httpMock.expectOne('/api/admissions/pending-count');
    expect(req.request.method).toBe('GET');
    req.flush({ pending: 3 });
  });

  afterEach(() => {
    httpMock.verify();
    service.ngOnDestroy();
  });

  it('initializes pending count from server', () => {
    expect(service.pendingCount()).toBe(3);
  });

  it('fetches list of admissions with filters', () => {
    service.list('Pending', true, 0, 10, 'Patil').subscribe((res) => {
      expect(res.total).toBe(1);
      expect(res.rows.length).toBe(1);
    });

    const req = httpMock.expectOne((r) => r.url === '/api/admissions' && r.params.get('status') === 'Pending');
    expect(req.request.params.get('duplicate')).toBe('true');
    expect(req.request.params.get('search')).toBe('Patil');
    req.flush({ total: 1, rows: [{ id: 1, surname: 'Patil', status: 'Pending' }] });
  });

  it('fetches admission detail', () => {
    service.getDetail(42).subscribe((res) => {
      expect(res.id).toBe(42);
      expect(res.status).toBe('Pending');
    });

    const req = httpMock.expectOne('/api/admissions/42');
    expect(req.request.method).toBe('GET');
    req.flush({ id: 42, status: 'Pending', duplicateFlag: false, form: {} });
  });

  it('approves an admission and refreshes pending count', () => {
    service.approve(10, { grNo: 'GR-100', academicYearId: 1, divisionId: 2, version: 0 }).subscribe();

    const approveReq = httpMock.expectOne('/api/admissions/10/approve');
    expect(approveReq.request.method).toBe('POST');
    approveReq.flush({ id: 10, status: 'Approved', studentId: 55 });

    const countReq = httpMock.expectOne('/api/admissions/pending-count');
    countReq.flush({ pending: 2 });
    expect(service.pendingCount()).toBe(2);
  });

  it('rejects an admission and refreshes pending count', () => {
    service.reject(10, { remarks: 'Not eligible', version: 0 }).subscribe();

    const rejectReq = httpMock.expectOne('/api/admissions/10/reject');
    expect(rejectReq.request.method).toBe('POST');
    rejectReq.flush({ id: 10, status: 'Rejected' });

    const countReq = httpMock.expectOne('/api/admissions/pending-count');
    countReq.flush({ pending: 2 });
    expect(service.pendingCount()).toBe(2);
  });

  it('uploads responses file and refreshes pending count', () => {
    const file = new File(['content'], 'responses.xlsx', { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
    service.upload(file).subscribe((res) => {
      expect(res.added).toBe(5);
    });

    const uploadReq = httpMock.expectOne('/api/admissions/upload');
    expect(uploadReq.request.method).toBe('POST');
    uploadReq.flush({ batchId: 1, total: 5, added: 5, skipped: 0, duplicates: 0, invalid: [] });

    const countReq = httpMock.expectOne('/api/admissions/pending-count');
    countReq.flush({ pending: 8 });
    expect(service.pendingCount()).toBe(8);
  });
});
