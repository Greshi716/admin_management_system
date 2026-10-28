import { FormGroup } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ToastService } from '../../../core/toast/toast.service';
import { FeesService } from '../../fees/fees.service';
import { PickLists, Student, StudentsService } from '../students.service';
import { StudentFormComponent } from './student-form.component';

const LISTS: PickLists = {
  currentYear: { id: 1, name: '2026-27' },
  standards: [
    { id: 10, name: '5', displayOrder: 5, stream: null, active: true },
    { id: 11, name: '6', displayOrder: 6, stream: null, active: true },
  ],
  divisions: [{ id: 100, standardId: 10, name: 'A', active: true }],
  categories: [{ id: 7, code: 'GEN', name: 'General', active: true }],
};

const SAVED = { id: 42, grNo: 'GR-1' } as Student;

describe('StudentFormComponent (FR-001, FR-003, FR-005, FR-011, FR-017)', () => {
  let fixture: ComponentFixture<StudentFormComponent>;
  let component: StudentFormComponent;
  let element: HTMLElement;
  let service: {
    pickLists: ReturnType<typeof vi.fn>;
    register: ReturnType<typeof vi.fn>;
    nameMatches: ReturnType<typeof vi.fn>;
    putPhoto: ReturnType<typeof vi.fn>;
  };
  let toast: { success: ReturnType<typeof vi.fn>; warn: ReturnType<typeof vi.fn>; error: ReturnType<typeof vi.fn> };

  /** The protected form, reached through a typed view instead of `any`. */
  const form = () => (component as unknown as { form: FormGroup }).form;

  function fillValid(): void {
    form().patchValue({
      grNo: 'GR-1',
      surname: 'Testsurname',
      firstName: 'Alpha',
      gender: 'Male',
      dob: '2014-01-01',
      mobile1: '9000000001',
      address: 'Test address',
      categoryId: 7,
      admissionDate: '2020-06-01',
      admissionStandardId: 10,
      standardId: 10,
    });
    form().patchValue({ divisionId: 100 });
  }

  function click(label: string): void {
    const button = Array.from(element.querySelectorAll('button')).find((b) => b.textContent?.trim() === label);
    (button as HTMLButtonElement).click();
    fixture.detectChanges();
  }

  beforeEach(() => {
    service = {
      pickLists: vi.fn().mockReturnValue(of(LISTS)),
      register: vi.fn().mockReturnValue(of(SAVED)),
      nameMatches: vi.fn().mockReturnValue(of([])),
      putPhoto: vi.fn().mockReturnValue(of({ photoVersion: 1 })),
    };
    toast = { success: vi.fn(), warn: vi.fn(), error: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: StudentsService, useValue: service },
        { provide: FeesService, useValue: { studentFees: vi.fn(), saveBroughtForward: vi.fn() } },
        { provide: ToastService, useValue: toast },
      ],
    });
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(StudentFormComponent);
    component = fixture.componentInstance;
    element = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  it('FR-007: has no Brought forward section when registering a student', () => {
    expect(element.querySelector('[data-testid="brought-forward-section"]')).toBeNull();
  });

  it('does not save while required fields are empty and shows what is missing', () => {
    click('Save');

    expect(service.register).not.toHaveBeenCalled();
    expect(element.textContent).toContain('Enter the GR No.');
    expect(element.textContent).toContain('Enter the surname.');
  });

  it('sends the student with the blank optional fields as null', () => {
    fillValid();

    click('Save');

    expect(service.register).toHaveBeenCalledTimes(1);
    const sent = service.register.mock.calls[0][0];
    expect(sent.grNo).toBe('GR-1');
    expect(sent.middleName).toBeNull();
    expect(sent.aadhaar).toBeNull();
    expect(sent.divisionId).toBe(100);
  });

  it('shows the server field errors beside the fields', () => {
    fillValid();
    service.register.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 422,
            error: { fieldErrors: [{ field: 'grNo', message: 'This GR No. is already used.' }] },
          }),
      ),
    );

    click('Save');

    expect(element.textContent).toContain('This GR No. is already used.');
    expect(toast.error).not.toHaveBeenCalled();
  });

  it('warns about a student with the same name but still lets the admin save', () => {
    service.nameMatches.mockReturnValue(of([{ id: 3, grNo: 'GR-0', name: 'Testsurname Alpha', status: 'Active' }]));
    fillValid();

    (component as unknown as { checkNames(): void }).checkNames();
    fixture.detectChanges();

    expect(element.querySelector('[data-testid="name-matches"]')?.textContent).toContain('GR-0');
    click('Save');
    expect(service.register).toHaveBeenCalled();
  });

  it('clears the form after Save and add next and names the student just saved', () => {
    fillValid();

    click('Save and add next');

    expect(form().getRawValue().grNo).toBe('');
    expect(element.querySelector('[data-testid="last-saved"]')?.textContent).toContain('GR-1');
  });

  it('opens the profile after Save', () => {
    const navigate = TestBed.inject(Router).navigate as ReturnType<typeof vi.fn>;
    fillValid();

    click('Save');

    expect(navigate).toHaveBeenCalledWith(['/students', 42]);
  });

  it('says to add a division when the chosen standard has none this year', () => {
    form().patchValue({ standardId: 11 });
    fixture.detectChanges();

    expect(element.querySelector('[data-testid="no-division"]')?.textContent).toContain(
      'Add a division for this standard under Setup',
    );
  });

  it('sends the chosen photo after the student was created', () => {
    fillValid();
    (component as unknown as { photoChosen(f: File): void }).photoChosen(new File(['x'], 'p.png'));

    click('Save and add next');

    expect(service.putPhoto).toHaveBeenCalledWith(42, expect.any(File));
    expect(service.register.mock.invocationCallOrder[0]).toBeLessThan(service.putPhoto.mock.invocationCallOrder[0]);
  });

  it('keeps the student saved and shows the reason beside the photo when the photo is refused', () => {
    service.putPhoto.mockReturnValue(
      throwError(
        () => new HttpErrorResponse({ status: 422, error: { fieldErrors: [{ field: 'file', message: 'Not a photo.' }] } }),
      ),
    );
    fillValid();
    (component as unknown as { photoChosen(f: File): void }).photoChosen(new File(['x'], 'p.txt'));

    click('Save and add next');

    expect(element.querySelector('[data-testid="photo-error"]')?.textContent).toContain('Not a photo.');
    expect(element.querySelector('[data-testid="saved-banner"]')).not.toBeNull();
    // Not cleared yet: the admin decides, and the same student cannot be saved twice.
    expect(form().getRawValue().grNo).toBe('GR-1');
    click('Save');
    expect(service.register).toHaveBeenCalledTimes(1);
  });
});
