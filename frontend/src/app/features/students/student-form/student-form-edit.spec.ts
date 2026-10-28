import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ToastService } from '../../../core/toast/toast.service';
import { FeesService, StudentFees } from '../../fees/fees.service';
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
    get: ReturnType<typeof vi.fn>;
    putPhoto: ReturnType<typeof vi.fn>;
    deletePhoto: ReturnType<typeof vi.fn>;
    photoUrl: ReturnType<typeof vi.fn>;
  };
  let navigate: ReturnType<typeof vi.spyOn>;
  let fees: { studentFees: ReturnType<typeof vi.fn>; saveBroughtForward: ReturnType<typeof vi.fn> };
  let toast: { success: ReturnType<typeof vi.fn>; warn: ReturnType<typeof vi.fn>; error: ReturnType<typeof vi.fn> };

  /** The protected members the tests read; reached through a typed view instead of `any`. */
  interface FormView {
    form: { getRawValue(): { grNo: string; aadhaar: string; address: string }; patchValue(values: { address?: string }): void };
  }
  const component = () => fixture.componentInstance as unknown as FormView;
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
      get: vi.fn().mockReturnValue(of({ id: 9, photoVersion: 4 } as Student)),
      putPhoto: vi.fn().mockReturnValue(of({ photoVersion: 5 })),
      deletePhoto: vi.fn().mockReturnValue(of(undefined)),
      photoUrl: vi.fn((id: number, version: number) => `/api/students/${id}/photo?v=${version}`),
    };
    fees = {
      studentFees: vi.fn().mockReturnValue(
        of({ broughtForward: { version: null, amount: 0, note: null } } as unknown as StudentFees),
      ),
      saveBroughtForward: vi.fn().mockReturnValue(of({ version: 0, amount: 2500, note: 'Test note' })),
    };
    toast = { success: vi.fn(), warn: vi.fn(), error: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: StudentsService, useValue: service },
        { provide: FeesService, useValue: fees },
        { provide: ToastService, useValue: toast },
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

  it('has no category, standard, division or Save and add next', () => {
    expect(element.querySelector('#categoryId')).toBeNull();
    expect(element.querySelector('#standardId')).toBeNull();
    expect(element.querySelector('#divisionId')).toBeNull();
    expect(Array.from(element.querySelectorAll('button')).map((b) => b.textContent?.trim())).not.toContain('Save and add next');
  });

  it('FR-005: shows the stored photo and replaces it at once with the chosen file', () => {
    expect(element.querySelector<HTMLImageElement>('[data-testid="photo-preview"]')?.getAttribute('src')).toBe('/api/students/9/photo?v=4');

    const input = element.querySelector<HTMLInputElement>('input[type="file"]') as HTMLInputElement;
    const file = new File(['x'], 'new.png', { type: 'image/png' });
    Object.defineProperty(input, 'files', { value: { item: () => file, length: 1 } });
    input.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(service.putPhoto).toHaveBeenCalledWith(9, file);
  });

  it('FR-005: removes the stored photo and then offers to choose one', () => {
    click('Remove photo');

    expect(service.deletePhoto).toHaveBeenCalledWith(9);
    expect(element.textContent).toContain('Choose photo');
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

  describe('Brought forward (FR-007, FR-009, FR-011)', () => {
    const field = (id: string) => element.querySelector<HTMLInputElement>(id) as HTMLInputElement;
    const type = (id: string, value: string) => {
      const input = field(id);
      input.value = value;
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();
    };

    it('shows the section in edit mode only, starting from the saved amount and note', () => {
      expect(element.querySelector('[data-testid="brought-forward-section"]')).not.toBeNull();
      expect(fees.studentFees).toHaveBeenCalledWith(9);
      expect(field('#bfAmount').value).toBe('0.00');
      expect(field('#bfNote').value).toBe('');
      expect(element.textContent).toContain('A short reason, at most 200 characters.');
    });

    it('saves the amount and note on their own with a null version the first time', () => {
      type('#bfAmount', '2,500');
      type('#bfNote', 'Test note');

      click('Save brought forward');

      expect(fees.saveBroughtForward).toHaveBeenCalledWith(9, { version: null, amount: 2500, note: 'Test note' });
      expect(service.update).not.toHaveBeenCalled();
      expect(toast.success).toHaveBeenCalledWith('The brought-forward amount was saved.');
    });

    it('sends the version of the last save the next time', () => {
      type('#bfAmount', '2500');
      click('Save brought forward');
      type('#bfAmount', '1000.50');

      click('Save brought forward');

      expect(fees.saveBroughtForward).toHaveBeenLastCalledWith(9, { version: 0, amount: 1000.5, note: 'Test note' });
    });

    it('Clear saves 0 and no note', () => {
      click('Clear');

      expect(fees.saveBroughtForward).toHaveBeenCalledWith(9, { version: null, amount: 0, note: null });
    });

    it('puts the server field errors beside the amount and the note', () => {
      fees.saveBroughtForward.mockReturnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 422,
              error: {
                fieldErrors: [
                  { field: 'amount', message: 'The amount cannot be negative.' },
                  { field: 'note', message: 'The note can be at most 200 characters.' },
                ],
              },
            }),
        ),
      );
      type('#bfAmount', '-5');

      click('Save brought forward');

      expect(element.textContent).toContain('The amount cannot be negative.');
      expect(element.textContent).toContain('The note can be at most 200 characters.');
    });

    it('flags text that is not a number and sends nothing', () => {
      type('#bfAmount', 'abc');

      click('Save brought forward');

      expect(fees.saveBroughtForward).not.toHaveBeenCalled();
      expect(element.textContent).toContain('Enter the amount as a number');
    });

    it('shows the latest saved values when the save answers STALE_VERSION', () => {
      fees.saveBroughtForward.mockReturnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 409,
              error: { code: 'STALE_VERSION', current: { version: 3, amount: 700.25, note: 'Latest' } },
            }),
        ),
      );
      click('Save brought forward');

      expect(field('#bfAmount').value).toBe('700.25');
      expect(field('#bfNote').value).toBe('Latest');
    });
  });
});
