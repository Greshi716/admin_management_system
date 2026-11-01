import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { ToastService } from '../../../core/toast/toast.service';
import { StudentsService } from '../../students/students.service';
import { AdmissionsService } from '../admissions.service';
import { AdmissionReviewComponent } from './admission-review.component';

describe('AdmissionReviewComponent', () => {
  let component: AdmissionReviewComponent;
  let fixture: ComponentFixture<AdmissionReviewComponent>;
  let mockAdmissionsService: {
    getDetail: ReturnType<typeof vi.fn>;
    getAcademicYears: ReturnType<typeof vi.fn>;
    approve: ReturnType<typeof vi.fn>;
    reject: ReturnType<typeof vi.fn>;
  };
  let mockStudentsService: {
    pickLists: ReturnType<typeof vi.fn>;
  };
  let mockToast: {
    success: ReturnType<typeof vi.fn>;
    error: ReturnType<typeof vi.fn>;
    info: ReturnType<typeof vi.fn>;
    warn: ReturnType<typeof vi.fn>;
  };
  let router: Router;

  const mockDetail = {
    id: 15,
    status: 'Pending' as const,
    duplicateFlag: true,
    duplicateOf: { students: [101] },
    duplicateMatches: [
      { type: 'Student' as const, id: 101, label: 'GR-101 - Patil, Rahul (Active)', link: '/students/101' },
    ],
    form: {
      surname: 'Patil',
      firstName: 'Rahul',
      middleName: 'Ashok',
      gender: 'Male',
      dob: '2016-04-12',
      mobile1: '9876543210',
      address: '123 Main Street',
      admissionStandard: 'Std 1',
      admissionStandardId: 1,
      category: 'General',
    },
    version: 0,
    submittedAt: '2026-06-01T10:00:00Z',
    remarks: null,
    studentId: null,
    reviewedBy: null,
    reviewedAt: null,
  };

  beforeEach(async () => {
    mockAdmissionsService = {
      getDetail: vi.fn().mockReturnValue(of(mockDetail)),
      getAcademicYears: vi.fn().mockReturnValue(
        of([{ id: 1, name: '2026-2027', startDate: '2026-04-01', endDate: '2027-03-31' }])
      ),
      approve: vi.fn(),
      reject: vi.fn(),
    };

    mockStudentsService = {
      pickLists: vi.fn().mockReturnValue(
        of({
          currentYear: { id: 1, name: '2026-2027' },
          standards: [{ id: 1, name: 'Std 1', displayOrder: 1, stream: null, active: true }],
          divisions: [{ id: 10, standardId: 1, name: 'A', active: true }],
          categories: [{ id: 5, code: 'GEN', name: 'General', active: true }],
        })
      ),
    };

    mockToast = {
      success: vi.fn(),
      error: vi.fn(),
      info: vi.fn(),
      warn: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [AdmissionReviewComponent],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: (key: string) => (key === 'id' ? '15' : null),
              },
            },
          },
        },
        { provide: AdmissionsService, useValue: mockAdmissionsService },
        { provide: StudentsService, useValue: mockStudentsService },
        { provide: ToastService, useValue: mockToast },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate');

    fixture = TestBed.createComponent(AdmissionReviewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('creates the review component and loads detail and picklists', () => {
    expect(component).toBeTruthy();
    expect(mockAdmissionsService.getDetail).toHaveBeenCalledWith(15);
    expect(component['detail']()?.id).toBe(15);
    expect(component['form'].controls.surname.value).toBe('Patil');
    expect(component['form'].controls.divisionId.value).toBe(10);
    expect(component['form'].controls.categoryId.value).toBe(5);
  });

  it('shows duplicate warning when duplicateFlag is true', () => {
    expect(component['detail']()?.duplicateFlag).toBe(true);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('[data-testid="duplicate-warning-banner"]')).toBeTruthy();
  });

  it('approves admission on confirm', () => {
    component['form'].controls.grNo.setValue('GR-2026-001');
    mockAdmissionsService.approve.mockReturnValue(of({ id: 15, studentId: 205 }));

    component.confirmApprove();

    expect(mockAdmissionsService.approve).toHaveBeenCalled();
    expect(mockToast.success).toHaveBeenCalledWith('Admission approved. Student record created.');
    expect(router.navigate).toHaveBeenCalledWith(['/students', 205]);
  });

  it('rejects admission on confirm with remarks', () => {
    component['rejectRemarks'].setValue('Incomplete form');
    mockAdmissionsService.reject.mockReturnValue(of({ id: 15, status: 'Rejected' }));

    component.confirmReject();

    expect(mockAdmissionsService.reject).toHaveBeenCalledWith(15, { remarks: 'Incomplete form', version: 0 });
    expect(mockToast.success).toHaveBeenCalledWith('Admission request rejected.');
  });
});
