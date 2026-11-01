import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ToastService } from '../../../core/toast/toast.service';
import { StudentsService } from '../../students/students.service';
import { DialogComponent } from '../../../shared/ui/dialog.component';
import { PageSkeletonComponent } from '../../../shared/ui/page-skeleton.component';
import {
  AcademicYearOption,
  AdmissionDetail,
  AdmissionsService,
  ApproveInput,
  RejectInput,
} from '../admissions.service';

@Component({
  selector: 'app-admission-review',
  imports: [CommonModule, ReactiveFormsModule, RouterLink, DialogComponent, PageSkeletonComponent],
  templateUrl: './admission-review.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdmissionReviewComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly admissionsService = inject(AdmissionsService);
  private readonly studentsService = inject(StudentsService);
  private readonly toast = inject(ToastService);

  protected readonly id = signal<number>(0);
  protected readonly detail = signal<AdmissionDetail | null>(null);
  protected readonly loading = signal<boolean>(true);
  protected readonly busy = signal<boolean>(false);
  protected readonly errorMessage = signal<string | null>(null);

  // Pick lists
  protected readonly academicYears = signal<AcademicYearOption[]>([]);
  protected readonly divisions = signal<{ id: number; standardId: number; name: string }[]>([]);
  protected readonly categories = signal<{ id: number; name: string }[]>([]);

  // Dialogs
  protected readonly approveDialogOpen = signal<boolean>(false);
  protected readonly rejectDialogOpen = signal<boolean>(false);
  protected readonly rejectRemarks = new FormControl('', { nonNullable: true, validators: [Validators.required] });
  protected readonly rejectError = signal<string | null>(null);

  protected readonly form = new FormGroup({
    grNo: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    academicYearId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    divisionId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    categoryId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    admissionDate: new FormControl('', { nonNullable: true, validators: [Validators.required] }),

    // Personal details
    surname: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    firstName: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    middleName: new FormControl(''),
    gender: new FormControl<'Male' | 'Female' | 'Other'>('Male', { nonNullable: true, validators: [Validators.required] }),
    dob: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    birthPlace: new FormControl(''),
    fatherName: new FormControl(''),
    motherName: new FormControl(''),
    mobile1: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(/^\d{10}$/)] }),
    mobile2: new FormControl(''),
    address: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    aadhaar: new FormControl(''),
    religionCaste: new FormControl(''),
    previousSchool: new FormControl(''),
  });

  protected readonly isPending = computed(() => this.detail()?.status === 'Pending');
  protected readonly candidateName = computed(() => {
    const d = this.detail();
    if (!d) return '';
    const fn = d.form['firstName'] || '';
    const sn = d.form['surname'] || '';
    return `${sn}, ${fn}`.trim();
  });

  protected readonly admissionStandardName = computed(() => {
    return (this.detail()?.form['admissionStandard'] as string) || 'Standard';
  });

  protected readonly selectedDivisionName = computed(() => {
    const divId = this.form.controls.divisionId.value;
    const match = this.divisions().find((d) => d.id === divId);
    return match ? match.name : '';
  });

  protected readonly selectedYearName = computed(() => {
    const yearId = this.form.controls.academicYearId.value;
    const match = this.academicYears().find((y) => y.id === yearId);
    return match ? match.name : '';
  });

  ngOnInit(): void {
    const reqId = Number(this.route.snapshot.paramMap.get('id'));
    if (!reqId) {
      this.router.navigate(['/admissions']);
      return;
    }
    this.id.set(reqId);
    this.loadData();
  }

  loadData(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    this.admissionsService.getDetail(this.id()).subscribe({
      next: (det) => {
        this.detail.set(det);
        this.populateForm(det);
        this.loadPickLists(det);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.errorMessage.set(err.error?.message || 'Could not load admission request.');
      },
    });
  }

  private populateForm(det: AdmissionDetail): void {
    const f = det.form || {};
    const today = new Date().toISOString().substring(0, 10);

    this.form.patchValue({
      admissionDate: (f['admissionDate'] as string) || today,
      surname: (f['surname'] as string) || '',
      firstName: (f['firstName'] as string) || '',
      middleName: (f['middleName'] as string) || '',
      gender: ((f['gender'] as string) === 'Female' ? 'Female' : (f['gender'] as string) === 'Other' ? 'Other' : 'Male'),
      dob: (f['dob'] as string) || '',
      birthPlace: (f['birthPlace'] as string) || '',
      fatherName: (f['fatherName'] as string) || '',
      motherName: (f['motherName'] as string) || '',
      mobile1: (f['mobile1'] as string) || '',
      mobile2: (f['mobile2'] as string) || '',
      address: (f['address'] as string) || '',
      aadhaar: (f['aadhaar'] as string) || '',
      religionCaste: (f['religionCaste'] as string) || '',
      previousSchool: (f['previousSchool'] as string) || '',
    });

    if (det.status !== 'Pending') {
      this.form.disable();
    }
  }

  private loadPickLists(det: AdmissionDetail): void {
    this.admissionsService.getAcademicYears().subscribe({
      next: (years) => {
        this.academicYears.set(years);

        // Find default year: current year or first year
        const currentYear = years[0];
        const yearId = currentYear ? currentYear.id : null;
        if (yearId && !this.form.controls.academicYearId.value) {
          this.form.controls.academicYearId.setValue(yearId);
        }

        this.studentsService.pickLists(yearId || undefined).subscribe({
          next: (picks) => {
            const stdId = det.form['admissionStandardId'] as number;
            const filteredDivs = picks.divisions.filter((d) => !stdId || d.standardId === stdId);
            this.divisions.set(filteredDivs);
            this.categories.set(picks.categories);

            if (filteredDivs.length > 0 && !this.form.controls.divisionId.value) {
              this.form.controls.divisionId.setValue(filteredDivs[0].id);
            }

            // Match category if form had category name
            const rawCat = det.form['category'] as string;
            if (rawCat) {
              const matchedCat = picks.categories.find(
                (c) => c.name.toLowerCase() === rawCat.toLowerCase()
              );
              if (matchedCat) {
                this.form.controls.categoryId.setValue(matchedCat.id);
              }
            }
            if (!this.form.controls.categoryId.value && picks.categories.length > 0) {
              this.form.controls.categoryId.setValue(picks.categories[0].id);
            }

            this.loading.set(false);
          },
          error: () => this.loading.set(false),
        });
      },
      error: () => this.loading.set(false),
    });
  }

  onYearChange(yearId: number): void {
    this.form.controls.academicYearId.setValue(yearId);
    this.studentsService.pickLists(yearId).subscribe({
      next: (picks) => {
        const stdId = this.detail()?.form['admissionStandardId'] as number;
        const filteredDivs = picks.divisions.filter((d) => !stdId || d.standardId === stdId);
        this.divisions.set(filteredDivs);
        if (filteredDivs.length > 0) {
          this.form.controls.divisionId.setValue(filteredDivs[0].id);
        } else {
          this.form.controls.divisionId.setValue(null);
        }
      },
    });
  }

  openApproveDialog(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.toast.error('Please fill in all required fields marked in red.');
      return;
    }
    this.approveDialogOpen.set(true);
  }

  confirmApprove(): void {
    const d = this.detail();
    if (!d) return;

    this.busy.set(true);
    this.errorMessage.set(null);

    const f = this.form.getRawValue();
    const payload: ApproveInput = {
      grNo: f.grNo,
      academicYearId: f.academicYearId!,
      divisionId: f.divisionId!,
      categoryId: f.categoryId || undefined,
      admissionDate: f.admissionDate,
      form: {
        surname: f.surname,
        firstName: f.firstName,
        middleName: f.middleName,
        gender: f.gender,
        dob: f.dob,
        birthPlace: f.birthPlace,
        fatherName: f.fatherName,
        motherName: f.motherName,
        mobile1: f.mobile1,
        mobile2: f.mobile2,
        address: f.address,
        aadhaar: f.aadhaar,
        religionCaste: f.religionCaste,
        previousSchool: f.previousSchool,
      },
      version: d.version,
    };

    this.admissionsService.approve(this.id(), payload).subscribe({
      next: (res) => {
        this.busy.set(false);
        this.approveDialogOpen.set(false);
        this.toast.success('Admission approved. Student record created.');
        const studentId = res['studentId'];
        if (studentId) {
          this.router.navigate(['/students', studentId]);
        } else {
          this.router.navigate(['/admissions']);
        }
      },
      error: (err: HttpErrorResponse) => {
        this.busy.set(false);
        this.approveDialogOpen.set(false);
        if (err.error?.code === 'GR_NO_IN_USE') {
          this.errorMessage.set('This GR No. is already used by a student.');
        } else if (err.error?.code === 'ALREADY_REVIEWED') {
          this.errorMessage.set('This admission request has already been reviewed.');
          this.loadData();
        } else if (err.error?.code === 'STALE_VERSION') {
          this.errorMessage.set('The request was modified by someone else. Reloading latest.');
          this.loadData();
        } else if (err.error?.fieldErrors && err.error.fieldErrors.length > 0) {
          this.errorMessage.set(err.error.fieldErrors.map((fe: { message: string }) => fe.message).join(' '));
        } else {
          this.errorMessage.set(err.error?.detail || err.error?.message || 'Could not approve admission.');
        }
      },
    });
  }

  openRejectDialog(): void {
    this.rejectRemarks.reset();
    this.rejectError.set(null);
    this.rejectDialogOpen.set(true);
  }

  confirmReject(): void {
    const remarks = this.rejectRemarks.value.trim();
    if (!remarks) {
      this.rejectError.set('Enter the remarks to reject this request.');
      return;
    }

    const d = this.detail();
    if (!d) return;

    this.busy.set(true);
    this.rejectError.set(null);

    const payload: RejectInput = {
      remarks,
      version: d.version,
    };

    this.admissionsService.reject(this.id(), payload).subscribe({
      next: () => {
        this.busy.set(false);
        this.rejectDialogOpen.set(false);
        this.toast.success('Admission request rejected.');
        this.loadData();
      },
      error: (err: HttpErrorResponse) => {
        this.busy.set(false);
        this.rejectDialogOpen.set(false);
        if (err.error?.code === 'ALREADY_REVIEWED') {
          this.errorMessage.set('This admission request has already been reviewed.');
          this.loadData();
        } else {
          this.errorMessage.set(err.error?.detail || err.error?.message || 'Could not reject admission.');
        }
      },
    });
  }
}
