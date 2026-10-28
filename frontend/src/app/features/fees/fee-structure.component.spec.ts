import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ToastService } from '../../core/toast/toast.service';
import { FeeStructureComponent, RowEditor } from './fee-structure.component';
import { FeesService, FeeStructure } from './fees.service';

const DRAFT: FeeStructure = {
  yearId: 2,
  yearName: '2026-27',
  status: 'Draft',
  version: 4,
  studentCount: 3,
  rows: [
    {
      id: 11,
      version: 1,
      standardId: 10,
      standardName: '10',
      categoryId: null,
      categoryName: 'All categories',
      amount: 36000,
      registrationFee: 500,
      dueDate: '2026-07-15',
    },
  ],
};

describe('FeeStructureComponent (FR-001, FR-002, FR-011)', () => {
  let fixture: ComponentFixture<FeeStructureComponent>;
  let fees: Record<'structure' | 'choices' | 'addRow' | 'editRow' | 'deleteRow' | 'finalise', ReturnType<typeof vi.fn>>;

  interface View {
    editors(): RowEditor[];
    addRow(): void;
    save(editor: RowEditor): void;
    askFinalise(): void;
    confirmFinalise(): void;
    startRevising(): void;
    askDelete(editor: RowEditor): void;
    confirmRevise(): void;
    cancelRevise(): void;
    reviseAsk(): { studentCount: number } | null;
  }
  const view = () => fixture.componentInstance as unknown as View;
  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  function setUp(structure: FeeStructure = DRAFT): void {
    fees = {
      structure: vi.fn().mockReturnValue(of(structure)),
      choices: vi.fn().mockReturnValue(
        of({
          standards: [
            { id: 10, name: '10', displayOrder: 10, active: true },
            { id: 11, name: 'Old std', displayOrder: 11, active: false },
          ],
          categories: [
            { id: 3, code: 'OS', name: 'O/S', active: true },
            { id: 4, code: 'OLD', name: 'Old cat', active: false },
          ],
        }),
      ),
      addRow: vi.fn().mockReturnValue(of({})),
      editRow: vi.fn().mockReturnValue(of({})),
      deleteRow: vi.fn().mockReturnValue(of(undefined)),
      finalise: vi.fn().mockReturnValue(of({ ...structure, status: 'Finalised', version: 5 })),
    };
    TestBed.configureTestingModule({
      providers: [
        { provide: FeesService, useValue: fees },
        { provide: ToastService, useValue: { success: vi.fn(), error: vi.fn(), warn: vi.fn() } },
        {
          provide: HttpClient,
          useValue: { post: vi.fn().mockReturnValue(of({ rows: [{ id: 2, name: '2026-27', current: true }] })) },
        },
      ],
    });
    fixture = TestBed.createComponent(FeeStructureComponent);
    fixture.detectChanges();
  }

  it('opens on the current year with its rows, amounts with two decimals and the Draft tag', () => {
    setUp();

    expect(fees.structure).toHaveBeenCalledWith(2);
    expect(text()).toContain('Fee structure for 2026-27');
    expect(text()).toContain('Draft');
    const amount = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('input[aria-label="Yearly fee"]');
    expect(amount?.value).toBe('36000.00');
  });

  it('sends the typed amounts as numbers with the structure version the screen loaded', () => {
    setUp();
    const editor = view().editors()[0];
    editor.form.patchValue({ amount: '37,000.50', registrationFee: '' });

    view().save(editor);

    const sent = fees.editRow.mock.calls[0];
    expect(sent[0]).toBe(2);
    expect(sent[1]).toBe(11);
    expect(sent[2]).toMatchObject({ amount: 37000.5, registrationFee: null, structureVersion: 4, version: 1 });
  });

  it('flags text that is not a number beside the field and sends nothing', () => {
    setUp();
    const editor = view().editors()[0];
    editor.form.patchValue({ amount: 'abc' });

    view().save(editor);

    expect(fees.editRow).not.toHaveBeenCalled();
    expect(editor.form.controls.amount.errors?.['server']).toContain('number');
  });

  it('puts the field errors of a refused save beside the fields', () => {
    setUp();
    const editor = view().editors()[0];
    fees.editRow.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 422,
            error: { fieldErrors: [{ field: 'amount', message: 'The amount cannot be negative.' }] },
          }),
      ),
    );

    view().save(editor);

    expect(editor.form.controls.amount.errors?.['server']).toBe('The amount cannot be negative.');
  });

  it('reloads the structure when the save answers STALE_VERSION', () => {
    setUp();
    const editor = view().editors()[0];
    fees.editRow.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409, error: { code: 'STALE_VERSION', current: DRAFT } })),
    );

    view().save(editor);

    expect(fees.structure).toHaveBeenCalledTimes(2);
  });

  it('offers an inactive standard or category only in the row that holds it (FR-001)', () => {
    setUp();
    view().addRow();
    fixture.detectChanges();

    const rows = (fixture.nativeElement as HTMLElement).querySelectorAll('[data-testid="fee-row"]');
    const options = (row: Element, label: string) =>
      Array.from(row.querySelectorAll(`select[aria-label="${label}"] option`)).map((option) => option.textContent?.trim());
    expect(options(rows[0], 'Standard')).toEqual(['Choose…', '10']);
    expect(options(rows[1], 'Standard')).toEqual(['Choose…', '10']);
    expect(options(rows[1], 'Category')).toEqual(['All categories', 'O/S']);

    const first = view().editors()[0];
    first.form.patchValue({ standardId: 11, categoryId: 4 });
    fixture.detectChanges();
    expect(options(rows[0], 'Standard')).toEqual(['Choose…', '10', 'Old std']);
    expect(options(rows[0], 'Category')).toEqual(['All categories', 'O/S', 'Old cat']);
    expect(options(rows[1], 'Standard')).toEqual(['Choose…', '10']);
  });

  it('adds a new empty row for Add row', () => {
    setUp();

    view().addRow();

    expect(view().editors().length).toBe(2);
    expect(view().editors()[1].id).toBeNull();
  });

  it('finalises with the structure version and then shows no Add row or Finalise button', () => {
    setUp();

    view().askFinalise();
    view().confirmFinalise();
    fixture.detectChanges();

    expect(fees.finalise).toHaveBeenCalledWith(2, 4);
    expect(text()).toContain('Finalised');
    expect(text()).not.toContain('Add row');
  });

  describe('revise (FR-003, FR-004)', () => {
    const FINALISED: FeeStructure = { ...DRAFT, status: 'Finalised', version: 5, studentCount: 5 };
    const needsConfirmation = () =>
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { code: 'REVISE_CONFIRMATION_REQUIRED', current: { studentCount: 5 } },
          }),
      );

    it('shows Revise and no Add row, Save row or Finalise for a finalised year', () => {
      setUp(FINALISED);

      expect(text()).toContain('Revise');
      expect(text()).not.toContain('Add row');
      expect(text()).not.toContain('Save row');
      expect(text()).not.toContain('Finalise structure');
    });

    it('Revise opens the rows for editing with the notice that the year is being revised', () => {
      setUp(FINALISED);

      view().startRevising();
      fixture.detectChanges();

      expect(text()).toContain('This year is being revised.');
      expect(text()).toContain('Save row');
      expect(text()).toContain('Add row');
    });

    it('asks for confirmation with the student count and sends nothing changed before it', () => {
      setUp(FINALISED);
      fees.editRow.mockReturnValueOnce(needsConfirmation());
      view().startRevising();
      const editor = view().editors()[0];
      editor.form.patchValue({ amount: '37000' });

      view().save(editor);

      expect(view().reviseAsk()?.studentCount).toBe(5);
      expect(fees.editRow).toHaveBeenCalledTimes(1);
      expect(fees.editRow.mock.calls[0][3]).toBe(false);
    });

    it('Confirm sends the same request again with confirm=true', () => {
      setUp(FINALISED);
      fees.editRow.mockReturnValueOnce(needsConfirmation());
      view().startRevising();
      const editor = view().editors()[0];
      editor.form.patchValue({ amount: '37000' });
      view().save(editor);

      view().confirmRevise();

      expect(fees.editRow).toHaveBeenCalledTimes(2);
      expect(fees.editRow.mock.calls[1][3]).toBe(true);
      expect(fees.editRow.mock.calls[1][2]).toMatchObject({ amount: 37000, structureVersion: 5 });
      expect(view().reviseAsk()).toBeNull();
    });

    it('Cancel changes nothing', () => {
      setUp(FINALISED);
      fees.editRow.mockReturnValueOnce(needsConfirmation());
      view().startRevising();
      const editor = view().editors()[0];
      view().save(editor);

      view().cancelRevise();

      expect(fees.editRow).toHaveBeenCalledTimes(1);
      expect(view().reviseAsk()).toBeNull();
    });

    it('a delete in a finalised year goes straight to the warning, then repeats with confirm=true', () => {
      setUp(FINALISED);
      fees.deleteRow.mockReturnValueOnce(needsConfirmation());
      view().startRevising();

      view().askDelete(view().editors()[0]);
      expect(fees.deleteRow).toHaveBeenCalledWith(2, 11, 1, 5, false);
      expect(view().reviseAsk()?.studentCount).toBe(5);

      view().confirmRevise();
      expect(fees.deleteRow).toHaveBeenLastCalledWith(2, 11, 1, 5, true);
    });

    it('reloads the structure on a stale answer before any warning', () => {
      setUp(FINALISED);
      fees.editRow.mockReturnValueOnce(
        throwError(() => new HttpErrorResponse({ status: 409, error: { code: 'STALE_VERSION', current: FINALISED } })),
      );
      view().startRevising();

      view().save(view().editors()[0]);

      expect(view().reviseAsk()).toBeNull();
      expect(fees.structure).toHaveBeenCalledTimes(2);
    });
  });
});
