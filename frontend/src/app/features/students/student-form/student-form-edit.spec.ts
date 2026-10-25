import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ToastService } from '../../../core/toast/toast.service';
import { PickLists, Student, StudentInput, StudentsService } from '../students.service';
import { StudentFormComponent } from './student-form.component';

const LISTS: PickLists = {
  currentYear: { id: 1, name: '2026-27' },
  standards: [{ id: 10, name: '5', displayOrder: 5, stream: null, active: true }],
  divisions: [{ id: 100, standardId: 10, name: 'A', active: true }],
  categories: [{ id: 7, code: 'GEN', name: 'General', active: true }],
};

const LOADED: StudentInput = {
  version: 2,
  grNo: 'GR-9',
  surname: 'Testsurname',
  firstName: 'Alpha',
  middleName: null,
  gender: 'Male',
  dob: '2012-01-01',
  mobile1: '9000000009',
  address: 'Test address',
  aadhaar: '123456789012',
  religionCaste: 'Test religion',
  admissionDate: '2020-06-01',
  admissionStandardId: 10,
};

describe('StudentFormComponent in edit mode (FR-004, FR-018)', () => {
  let fixture: ComponentFixture<StudentFormComponent>;
  let element: HTMLElement;
  let service: {
    pickLists: ReturnType<typeof vi.fn>;
    editForm: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
    nameMatches: ReturnType<typeof vi.fn>;
  };
  let navigate: ReturnType<typeof vi.spyOn>;

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const component = () => fixture.componentInstance as any;
  const click = (label: string) => {
    const button = Array.from(element.querySelectorAll('button')).find((b) => b.textContent?.trim() === label);
    (button as HTMLButtonElement).click();
    fixture.detectChanges();
  };

  beforeEach(() => {
    service = {
      pickLists: vi.fn().mockReturnValue(of(LISTS)),
      editForm: vi.fn().mockReturnValue(of(LOADED)),
      update: vi.fn().mockReturnValue(of({ id: 9, grNo: 'GR-9' } as Student)),
      nameMatches: vi.fn().mockReturnValue(of([])),
    };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: StudentsService, useValue: service },
        { provide: ToastService, useValue: { success: vi.fn(), warn: vi.fn(), error: vi.fn() } },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: '9' }) } } },
      ],
    });
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(StudentFormComponent);
    element = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  it('loads the edit values, with the full Aadhaar, from the edit endpoint', () => {
    expect(service.editForm).toHaveBeenCalledWith(9);
    expect(component().form.getRawValue().grNo).toBe('GR-9');
    expect(component().form.getRawValue().aadhaar).toBe('123456789012');
    expect(element.querySelector('h1')?.textContent).toBe('Edit student');
  });

  it('has no category, standard, division, photo or Save and add next', () => {
    expect(element.querySelector('#categoryId')).toBeNull();
    expect(element.querySelector('#standardId')).toBeNull();
    expect(element.querySelector('#divisionId')).toBeNull();
    expect(element.querySelector('app-photo-upload')).toBeNull();
    expect(Array.from(element.querySelectorAll('button')).map((b) => b.textContent?.trim())).not.toContain('Save and add next');
  });

  it('saves with the loaded version and returns to the profile', () => {
    component().form.patchValue({ address: 'New test address' });

    click('Save');

    expect(service.update).toHaveBeenCalledWith(9, expect.objectContaining({ version: 2, address: 'New test address' }));
    expect(navigate).toHaveBeenCalledWith(['/students', 9]);
  });

  it('shows the latest values on a stale save and saves against the latest version when the typed values are kept', () => {
    service.update.mockReturnValueOnce(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { code: 'STALE_VERSION', current: { version: 3, grNo: 'GR-9', address: 'Other address' } },
          }),
      ),
    );
    click('Save');
    fixture.detectChanges();
    expect(element.querySelector('[data-testid="latest-values"]')?.textContent).toContain('Other address');
    expect(navigate).not.toHaveBeenCalled();

    click('Keep what I typed');
    click('Save');

    expect(service.update).toHaveBeenLastCalledWith(9, expect.objectContaining({ version: 3 }));
  });

  it('loads the saved record again when the latest values are chosen', () => {
    service.update.mockReturnValueOnce(
      throwError(() => new HttpErrorResponse({ status: 409, error: { code: 'STALE_VERSION', current: { version: 3 } } })),
    );
    click('Save');
    service.editForm.mockReturnValue(of({ ...LOADED, version: 3, address: 'Other address' }));

    click('Use the latest values');

    expect(component().form.getRawValue().address).toBe('Other address');
    click('Save');
    expect(service.update).toHaveBeenLastCalledWith(9, expect.objectContaining({ version: 3 }));
  });
});
