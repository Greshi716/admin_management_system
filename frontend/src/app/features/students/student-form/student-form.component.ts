import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, effect, ElementRef, inject, OnInit, signal, viewChild } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { problemOf, STALE_VERSION } from '../../../core/http/api-problem';
import { ToastService } from '../../../core/toast/toast.service';
import { applyFieldErrors, serverMessage } from '../../../shared/forms/api-error';
import { StaleField, StaleVersionDialogComponent } from '../../../shared/forms/stale-version-dialog.component';
import { PageSkeletonComponent } from '../../../shared/ui/page-skeleton.component';
import { PhotoUploadComponent } from '../../../shared/ui/photo-upload.component';
import { NameMatch, PickLists, StudentInput, StudentsService } from '../students.service';

type Gender = 'Male' | 'Female' | 'Other' | '';

/**
 * The student form (FR-001, FR-003, FR-005, FR-011, FR-017), used to register a student here and to edit one later.
 * The fields follow the order of the screen so the admin can enter a whole student with the keyboard only. The
 * server decides what is valid; its field errors are shown beside the field. A student with the same names only
 * produces a warning (FR-003). The photo is sent after the student has been created, so a refused photo never
 * loses the student.
 */
@Component({
  selector: 'app-student-form',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    NgTemplateOutlet,
    PageSkeletonComponent,
    PhotoUploadComponent,
    StaleVersionDialogComponent,
  ],
  templateUrl: './student-form.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StudentFormComponent implements OnInit {
  private readonly students = inject(StudentsService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  private readonly grNoInput = viewChild<ElementRef<HTMLInputElement>>('grNoInput');
  private readonly photoUpload = viewChild(PhotoUploadComponent);

  protected readonly serverMessage = serverMessage;
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly pickLists = signal<PickLists | null>(null);
  protected readonly matches = signal<NameMatch[]>([]);
  protected readonly photoError = signal<string | null>(null);
  /** Set once the student is stored; the form is then locked so the same student cannot be saved twice. */
  protected readonly saved = signal<{ id: number; grNo: string } | null>(null);
  /** The student being edited (route /students/:id/edit); null when registering. */
  protected readonly editId = signal<number | null>(null);
  /** The latest saved record after a stale save; shown in the stale-version dialog. */
  protected readonly stale = signal<Record<string, unknown> | null>(null);
  protected readonly staleFields: StaleField[] = [
    { field: 'grNo', label: 'GR No.' },
    { field: 'surname', label: 'Surname' },
    { field: 'firstName', label: 'First name' },
    { field: 'address', label: 'Address' },
    { field: 'mobile1', label: 'Mobile 1' },
    { field: 'status', label: 'Status' },
  ];
  private version = 0;
  /** The message after "Save and add next", naming the student just saved. */
  protected readonly lastSaved = signal<string | null>(null);

  private photoFile: File | null = null;

  protected readonly form = new FormGroup({
    grNo: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    surname: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    firstName: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    middleName: new FormControl('', { nonNullable: true }),
    gender: new FormControl<Gender>('', { nonNullable: true, validators: [Validators.required] }),
    dob: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    birthPlace: new FormControl('', { nonNullable: true }),
    fatherName: new FormControl('', { nonNullable: true }),
    motherName: new FormControl('', { nonNullable: true }),
    mobile1: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    mobile2: new FormControl('', { nonNullable: true }),
    address: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    aadhaar: new FormControl('', { nonNullable: true }),
    religionCaste: new FormControl('', { nonNullable: true }),
    categoryId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    admissionDate: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    admissionStandardId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    previousSchool: new FormControl('', { nonNullable: true }),
    standardId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    divisionId: new FormControl<number | null>(null, { validators: [Validators.required] }),
  });

  private readonly chosenStandard = signal<number | null>(null);

  /** The divisions of the chosen standard in the current year. */
  protected readonly divisions = computed(() => {
    const standard = this.chosenStandard();
    return standard === null ? [] : (this.pickLists()?.divisions ?? []).filter((d) => d.standardId === standard);
  });
  protected readonly noDivision = computed(() => this.chosenStandard() !== null && this.divisions().length === 0);

  constructor() {
    // The GR No. field only exists once the pick-lists are loaded; focus it as soon as it appears (first field).
    let focused = false;
    effect(() => {
      const field = this.grNoInput();
      if (field && !focused) {
        focused = true;
        field.nativeElement.focus();
      }
    });
  }

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam !== null) {
      this.startEdit(Number(idParam));
      return;
    }
    this.form.controls.standardId.valueChanges.subscribe((standard) => {
      this.chosenStandard.set(standard);
      this.form.controls.divisionId.reset(null);
    });
    this.students.pickLists().subscribe({
      next: (lists) => {
        this.pickLists.set(lists);
        this.loading.set(false);
      },
      error: () => this.loading.set(false), // the error interceptor has shown the reason
    });
  }

  /** Edit mode: the category and the class belong to the yearly class record, so they are not part of this form. */
  private startEdit(id: number): void {
    this.editId.set(id);
    for (const control of [this.form.controls.categoryId, this.form.controls.standardId, this.form.controls.divisionId]) {
      control.clearValidators();
      control.updateValueAndValidity();
    }
    forkJoin({ values: this.students.editForm(id), lists: this.students.pickLists() }).subscribe({
      next: ({ values, lists }) => {
        this.pickLists.set(lists);
        this.fill(values);
        this.loading.set(false);
      },
      error: () => this.loading.set(false), // the error interceptor has shown the reason
    });
  }

  private fill(values: StudentInput): void {
    this.version = values.version ?? 0;
    this.form.patchValue({
      grNo: values.grNo,
      surname: values.surname,
      firstName: values.firstName,
      middleName: values.middleName ?? '',
      gender: values.gender,
      dob: values.dob,
      birthPlace: values.birthPlace ?? '',
      fatherName: values.fatherName ?? '',
      motherName: values.motherName ?? '',
      mobile1: values.mobile1,
      mobile2: values.mobile2 ?? '',
      address: values.address,
      aadhaar: values.aadhaar ?? '',
      religionCaste: values.religionCaste ?? '',
      admissionDate: values.admissionDate,
      admissionStandardId: values.admissionStandardId,
      previousSchool: values.previousSchool ?? '',
    });
    this.form.markAsPristine();
  }

  /** Saves the edit against the version that was loaded; a stale save shows the latest values (FR-018). */
  protected saveEdit(): void {
    const id = this.editId();
    if (id === null || this.busy()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.students.update(id, { ...this.toInput(), version: this.version }).subscribe({
      next: (student) => {
        this.busy.set(false);
        this.toast.success(`Student ${student.grNo} was saved.`);
        void this.router.navigate(['/students', id]);
      },
      error: (error: unknown) => {
        this.busy.set(false);
        if (error instanceof HttpErrorResponse && problemOf(error.error)?.code === STALE_VERSION) {
          this.stale.set((problemOf(error.error)?.current ?? {}) as Record<string, unknown>);
          return;
        }
        const unmatched = applyFieldErrors(this.form, error);
        if (unmatched.length > 0) {
          this.toast.error(unmatched.map((e) => e.message).join(' '));
        }
      },
    });
  }

  /** Reloads the saved record (with the full Aadhaar) into the form and drops what was typed. */
  protected useLatest(): void {
    const id = this.editId();
    this.stale.set(null);
    if (id !== null) {
      this.students.editForm(id).subscribe((values) => this.fill(values));
    }
  }

  /** Keeps what was typed; the next save is made against the latest version. */
  protected keepMine(): void {
    const latest = this.stale();
    if (latest && typeof latest['version'] === 'number') {
      this.version = latest['version'];
    }
    this.stale.set(null);
  }

  /** Looks for students with the same names when the name fields lose focus; it never blocks saving. */
  protected checkNames(): void {
    const { surname, firstName, middleName } = this.form.getRawValue();
    if (!surname.trim() || !firstName.trim()) {
      this.matches.set([]);
      return;
    }
    this.students.nameMatches(surname.trim(), firstName.trim(), middleName.trim(), this.editId() ?? undefined).subscribe({
      next: (found) => this.matches.set(found),
      error: () => this.matches.set([]),
    });
  }

  protected photoChosen(file: File): void {
    this.photoError.set(null);
    this.photoFile = file;
    const saved = this.saved();
    if (saved) {
      this.sendPhoto(saved.id, file);
    }
  }

  protected photoRemoved(): void {
    this.photoFile = null;
    this.photoError.set(null);
  }

  protected save(addNext: boolean): void {
    if (this.busy() || this.saved()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.students.register(this.toInput()).subscribe({
      next: (student) => {
        this.saved.set({ id: student.id, grNo: student.grNo });
        this.afterCreate(student.id, student.grNo, addNext);
      },
      error: (error: unknown) => {
        this.busy.set(false);
        const unmatched = applyFieldErrors(this.form, error);
        if (unmatched.length > 0) {
          this.toast.error(unmatched.map((e) => e.message).join(' '));
        }
      },
    });
  }

  /** After a refused photo the student stays saved; the admin continues from here. */
  protected addAnother(): void {
    this.resetForm();
  }

  private afterCreate(id: number, grNo: string, addNext: boolean): void {
    const file = this.photoFile;
    if (!file) {
      this.finish(id, grNo, addNext);
      return;
    }
    this.students.putPhoto(id, file).subscribe({
      next: () => this.finish(id, grNo, addNext),
      error: (error: unknown) => {
        // The student is stored; only the photo failed. Show why beside the photo and wait for the admin.
        this.busy.set(false);
        this.photoError.set(messageOf(error));
        this.toast.warn(`Student ${grNo} was saved, but the photo was not stored.`);
      },
    });
  }

  private sendPhoto(id: number, file: File): void {
    this.busy.set(true);
    this.students.putPhoto(id, file).subscribe({
      next: () => {
        this.busy.set(false);
        this.photoError.set(null);
        this.toast.success('The photo was stored.');
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.photoError.set(messageOf(error));
      },
    });
  }

  private finish(id: number, grNo: string, addNext: boolean): void {
    this.busy.set(false);
    if (addNext) {
      this.resetForm();
      this.lastSaved.set(`Saved student with GR No. ${grNo}. You can add the next one.`);
      return;
    }
    this.toast.success(`Student ${grNo} was saved.`);
    void this.router.navigate(['/students', id]);
  }

  private resetForm(): void {
    this.form.reset();
    this.form.enable();
    this.matches.set([]);
    this.photoFile = null;
    this.photoError.set(null);
    this.saved.set(null);
    this.photoUpload()?.clear();
    this.chosenStandard.set(null);
    queueMicrotask(() => this.focusGrNo());
  }

  private focusGrNo(): void {
    this.grNoInput()?.nativeElement.focus();
  }

  private toInput(): StudentInput {
    const v = this.form.getRawValue();
    const blank = (text: string) => (text.trim() === '' ? null : text);
    return {
      grNo: v.grNo,
      surname: v.surname,
      firstName: v.firstName,
      middleName: blank(v.middleName),
      gender: v.gender,
      dob: v.dob,
      birthPlace: blank(v.birthPlace),
      fatherName: blank(v.fatherName),
      motherName: blank(v.motherName),
      mobile1: v.mobile1,
      mobile2: blank(v.mobile2),
      address: v.address,
      aadhaar: blank(v.aadhaar),
      religionCaste: blank(v.religionCaste),
      categoryId: v.categoryId,
      admissionDate: v.admissionDate,
      admissionStandardId: v.admissionStandardId,
      previousSchool: blank(v.previousSchool),
      standardId: v.standardId,
      divisionId: v.divisionId,
    };
  }
}

/** The server's reason for refusing a request, for showing beside the field it concerns. */
function messageOf(error: unknown): string {
  const problem = error instanceof HttpErrorResponse ? problemOf(error.error) : null;
  return problem?.fieldErrors?.[0]?.message ?? problem?.detail ?? problem?.title ?? 'The photo could not be stored.';
}
