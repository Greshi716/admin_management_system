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
  }
  const view = () => fixture.componentInstance as unknown as View;
  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  function setUp(structure: FeeStructure = DRAFT): void {
    fees = {
      structure: vi.fn().mockReturnValue(of(structure)),
      choices: vi.fn().mockReturnValue(
        of({ standards: [{ id: 10, name: '10', displayOrder: 10 }], categories: [{ id: 3, code: 'OS', name: 'O/S' }] }),
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
});
