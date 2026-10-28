import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, FormGroup } from '@angular/forms';
import { of, throwError } from 'rxjs';
import { ToastService } from '../../../core/toast/toast.service';
import { Enrollment, PickLists, StudentsService } from '../students.service';
import { ClassRecordDialogComponent } from './class-record-dialog.component';

const RECORD: Enrollment = {
  version: 3,
  academicYearId: 2,
  academicYearName: '2026-27',
  standardId: 10,
  divisionId: 100,
  stdDiv: '5-A',
  categoryId: 7,
  categoryName: 'General',
  yearStatus: 'Studying',
  feeLocked: false,
};

const LISTS: PickLists = {
  currentYear: { id: 2, name: '2026-27' },
  standards: [
    { id: 10, name: '5', displayOrder: 5, stream: null, active: true },
    { id: 11, name: '6', displayOrder: 6, stream: null, active: true },
  ],
  divisions: [
    { id: 100, standardId: 10, name: 'A', active: true },
    { id: 101, standardId: 11, name: 'B', active: true },
  ],
  categories: [
    { id: 7, code: 'GEN', name: 'General', active: true },
    { id: 8, code: 'OBC', name: 'Other', active: true },
  ],
};

describe('ClassRecordDialogComponent (FR-006, FR-018)', () => {
  let fixture: ComponentFixture<ClassRecordDialogComponent>;
  let service: { pickLists: ReturnType<typeof vi.fn>; updateEnrollment: ReturnType<typeof vi.fn> };
  let changed: ReturnType<typeof vi.fn<() => void>>;

  /** The protected members the tests use, reached through a typed view instead of `any`. */
  interface DialogView {
    form: FormGroup<{ standardId: FormControl<number | null>; divisionId: FormControl<number | null>; categoryId: FormControl<number | null> }>;
    divisions(): { id: number }[];
    stale(): unknown;
    save(): void;
    keepMine(): void;
    useLatest(): void;
    locked(): boolean;
  }
  const component = () => fixture.componentInstance as unknown as DialogView;

  beforeEach(() => {
    service = { pickLists: vi.fn().mockReturnValue(of(LISTS)), updateEnrollment: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        { provide: StudentsService, useValue: service },
        { provide: ToastService, useValue: { success: vi.fn(), error: vi.fn(), warn: vi.fn() } },
      ],
    });
    fixture = TestBed.createComponent(ClassRecordDialogComponent);
    fixture.componentRef.setInput('studentId', 5);
    fixture.componentRef.setInput('record', RECORD);
    changed = vi.fn<() => void>();
    fixture.componentInstance.changed.subscribe(() => changed());
    fixture.detectChanges();
  });

  it("loads the pick-lists of the record's own year and starts with its current values", () => {
    expect(service.pickLists).toHaveBeenCalledWith(2, 5);
    expect(component().form.getRawValue()).toEqual({ standardId: 10, divisionId: 100, categoryId: 7 });
    expect(component().divisions().map((d) => d.id)).toEqual([100]);
  });

  it('offers only the divisions of the chosen standard and clears the division when the standard changes', () => {
    component().form.controls.standardId.setValue(11);
    expect(component().divisions().map((d) => d.id)).toEqual([101]);
    expect(component().form.controls.divisionId.value).toBeNull();
  });

  it("saves with the record's version and tells the profile to reload", () => {
    service.updateEnrollment.mockReturnValue(of(RECORD));
    component().form.patchValue({ categoryId: 8 });

    component().save();

    expect(service.updateEnrollment).toHaveBeenCalledWith(5, 2, { version: 3, standardId: 10, divisionId: 100, categoryId: 8 });
    expect(changed).toHaveBeenCalled();
  });

  it('shows the latest values on a stale save and saves against the latest version when the typed values are kept', () => {
    const latest = { version: 4, stdDiv: '6-B', categoryName: 'Other' };
    service.updateEnrollment.mockReturnValueOnce(
      throwError(() => new HttpErrorResponse({ status: 409, error: { code: 'STALE_VERSION', current: latest } })),
    );
    component().save();
    expect(component().stale()).toEqual(latest);
    expect(changed).not.toHaveBeenCalled();

    component().keepMine();
    service.updateEnrollment.mockReturnValueOnce(of(RECORD));
    component().save();

    expect(service.updateEnrollment).toHaveBeenLastCalledWith(5, 2, expect.objectContaining({ version: 4 }));
  });

  it('reloads the profile when the latest values are chosen', () => {
    service.updateEnrollment.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409, error: { code: 'STALE_VERSION', current: { version: 4 } } })),
    );
    component().save();

    component().useLatest();

    expect(component().stale()).toBeNull();
    expect(changed).toHaveBeenCalled();
  });

  describe('fee lock (FR-008)', () => {
    const MESSAGE =
      'The fee structure for this year is finalised, so the standard and category cannot be changed. The division can.';
    const element = () => fixture.nativeElement as HTMLElement;

    function reopen(record: typeof RECORD): void {
      fixture.componentRef.setInput('record', null);
      fixture.detectChanges();
      fixture.componentRef.setInput('record', record);
      fixture.detectChanges();
    }

    it('shows no notice and keeps every field editable when the year is not finalised', () => {
      expect(component().locked()).toBe(false);
      expect(component().form.controls.standardId.disabled).toBe(false);
      expect(component().form.controls.categoryId.disabled).toBe(false);
      expect(element().querySelector('[data-testid="fee-lock-notice"]')).toBeNull();
    });

    it('fixes standard and category with the message when the record is fee locked, the division stays editable', () => {
      reopen({ ...RECORD, feeLocked: true });

      expect(component().locked()).toBe(true);
      expect(component().form.controls.standardId.disabled).toBe(true);
      expect(component().form.controls.categoryId.disabled).toBe(true);
      expect(component().form.controls.divisionId.disabled).toBe(false);
      expect(element().querySelector('[data-testid="fee-lock-notice"]')?.textContent?.trim()).toBe(MESSAGE);
    });

    it('saves only the division change with the unchanged standard and category', () => {
      reopen({ ...RECORD, feeLocked: true });
      service.updateEnrollment.mockReturnValue(of(RECORD));
      component().form.controls.divisionId.setValue(100);

      component().save();

      expect(service.updateEnrollment).toHaveBeenCalledWith(5, 2, { version: 3, standardId: 10, divisionId: 100, categoryId: 7 });
    });

    it('a 409 FEE_STRUCTURE_FINALISED fixes the fields and shows the same message', () => {
      service.updateEnrollment.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 409, error: { code: 'FEE_STRUCTURE_FINALISED' } })),
      );
      component().form.patchValue({ categoryId: 8 });

      component().save();
      fixture.detectChanges();

      expect(component().locked()).toBe(true);
      expect(component().form.controls.categoryId.value).toBe(7); // back to the record's value
      expect(component().form.controls.categoryId.disabled).toBe(true);
      expect(element().querySelector('[data-testid="fee-lock-notice"]')?.textContent?.trim()).toBe(MESSAGE);
      expect(changed).not.toHaveBeenCalled();
    });
  });
});
