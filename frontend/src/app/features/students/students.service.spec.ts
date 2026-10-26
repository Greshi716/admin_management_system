import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { StudentInput, StudentsService } from './students.service';

describe('StudentsService (FR-001, FR-003, FR-005)', () => {
  let service: StudentsService;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(StudentsService);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  const input: StudentInput = {
    grNo: 'GR-1',
    surname: 'Testsurname',
    firstName: 'Alpha',
    gender: 'Male',
    dob: '2012-01-01',
    mobile1: '9000000001',
    address: 'Test address',
    admissionDate: '2020-06-01',
    admissionStandardId: 1,
    standardId: 1,
    divisionId: 2,
    categoryId: 3,
  };

  it('registers with POST /api/students', () => {
    service.register(input).subscribe();

    const request = controller.expectOne('/api/students');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(input);
    request.flush({});
  });

  it('asks for name matches with the names as query parameters', () => {
    service.nameMatches('Testsurname', 'Alpha', 'Middle', 7).subscribe();

    const request = controller.expectOne((r) => r.url === '/api/students/name-matches');
    expect(request.request.params.get('surname')).toBe('Testsurname');
    expect(request.request.params.get('firstName')).toBe('Alpha');
    expect(request.request.params.get('middleName')).toBe('Middle');
    expect(request.request.params.get('excludeId')).toBe('7');
    request.flush([]);
  });

  it('leaves out an empty middle name and a missing excludeId', () => {
    service.nameMatches('A', 'B', '').subscribe();

    const request = controller.expectOne((r) => r.url === '/api/students/name-matches');
    expect(request.request.params.has('middleName')).toBe(false);
    expect(request.request.params.has('excludeId')).toBe(false);
    request.flush([]);
  });

  it('loads the pick-lists for a year and a student', () => {
    service.pickLists(4, 9).subscribe();

    const request = controller.expectOne((r) => r.url === '/api/student-pick-lists');
    expect(request.request.params.get('yearId')).toBe('4');
    expect(request.request.params.get('studentId')).toBe('9');
    request.flush({});
  });

  it('sends a photo as multipart form data to PUT /photo and removes it with DELETE', () => {
    const file = new File(['x'], 'photo.png', { type: 'image/png' });
    service.putPhoto(5, file).subscribe();

    const put = controller.expectOne('/api/students/5/photo');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body instanceof FormData).toBe(true);
    expect((put.request.body as FormData).get('file')).toBeInstanceOf(File);
    put.flush({ photoVersion: 1 });

    service.deletePhoto(5).subscribe();
    const remove = controller.expectOne('/api/students/5/photo');
    expect(remove.request.method).toBe('DELETE');
    remove.flush(null);
  });

  it('builds the edit, left, class record and history requests', () => {
    service.editForm(3).subscribe();
    expect(controller.expectOne('/api/students/3/edit').request.method).toBe('GET');

    service.update(3, { ...input, version: 2 }).subscribe();
    const update = controller.expectOne('/api/students/3');
    expect(update.request.method).toBe('PUT');
    expect(update.request.body.version).toBe(2);

    service.markLeft(3, 2).subscribe();
    expect(controller.expectOne('/api/students/3/left').request.body).toEqual({ version: 2 });

    service.updateEnrollment(3, 8, { version: 0, standardId: 1, divisionId: 2, categoryId: 3 }).subscribe();
    expect(controller.expectOne('/api/students/3/enrollments/8').request.method).toBe('PUT');

    service.history(3).subscribe();
    expect(controller.expectOne('/api/students/3/history').request.method).toBe('GET');
  });

  it('gives the photo address with its version', () => {
    expect(service.photoUrl(5, 77)).toBe('/api/students/5/photo?v=77');
  });
});
