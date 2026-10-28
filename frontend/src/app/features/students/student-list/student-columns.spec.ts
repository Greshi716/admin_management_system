import { FilterOptions } from '../students.service';
import { feeText, recordYearNote, studentColumns } from './student-columns';

const LISTS: FilterOptions = {
  standards: [
    { id: 10, name: 'Standard 5', displayOrder: 5, stream: null, active: true },
    { id: 11, name: 'Standard 6', displayOrder: 6, stream: null, active: false },
  ],
  divisions: [
    { id: 100, standardId: 10, name: 'A', yearId: 1, yearName: '2026-27', active: true },
    { id: 90, standardId: 11, name: 'B', yearId: 0, yearName: '2025-26', active: false },
  ],
  categories: [{ id: 7, code: 'GEN', name: 'General', active: false }],
};

describe('studentColumns (FR-008, FR-010)', () => {
  const visible = (columns = studentColumns(LISTS)) => columns.filter((column) => !column.hidden);

  it('shows photo, GR No., name, Std / Div, category, Total fee and status', () => {
    expect(visible().map((column) => column.header)).toEqual([
      'Photo',
      'GR No.',
      'Name',
      'Std / Div',
      'Category',
      'Total fee',
      'Status',
    ]);
  });

  it('keeps the ids the filters and the avatar need as hidden columns', () => {
    const hidden = studentColumns(LISTS).filter((column) => column.hidden).map((column) => column.field);

    expect(hidden).toEqual(expect.arrayContaining(['id', 'photoVersion', 'surname', 'firstName', 'recordYearId', 'recordYear']));
  });

  it('has no column for Aadhaar or religion/caste, so nothing can be filtered or sorted by them', () => {
    const fields = studentColumns(LISTS).map((column) => column.field);

    expect(fields).not.toContain('aadhaar');
    expect(fields).not.toContain('religionCaste');
  });

  it('filters Std / Div by standard and division ids, and Category by category id', () => {
    const columns = studentColumns(LISTS);
    const stdDiv = columns.find((column) => column.field === 'stdDiv');
    const category = columns.find((column) => column.field === 'category');

    expect(stdDiv?.dropdowns?.map((dropdown) => dropdown.field)).toEqual(['standardId', 'divisionId']);
    expect(stdDiv?.dropdowns?.[0].options).toEqual([
      { value: 10, label: 'Standard 5' },
      { value: 11, label: 'Standard 6' },
    ]);
    // inactive values and divisions of other years are offered too: a student keeps the values it was saved with
    expect(stdDiv?.dropdowns?.[1].options).toEqual([
      { value: 100, label: 'Standard 5 A (2026-27)' },
      { value: 90, label: 'Standard 6 B (2025-26)' },
    ]);
    expect(category?.dropdowns?.[0]).toMatchObject({ field: 'categoryId', options: [{ value: 7, label: 'General' }] });
  });

  it('does not offer a sort or filter on the photo column', () => {
    const photo = studentColumns(LISTS).find((column) => column.field === 'photo');

    expect(photo).toMatchObject({ custom: true, sortable: false, filterable: false });
  });

  it('still builds the columns when the pick-lists could not be loaded', () => {
    expect(visible(studentColumns(null))).toHaveLength(7);
  });

  it('badges Left differently from Active', () => {
    const status = studentColumns(LISTS).find((column) => column.field === 'status');

    expect(status?.badgeClass?.('Left')).not.toBe(status?.badgeClass?.('Active'));
  });
});

describe('recordYearNote (FR-008)', () => {
  it('shows the year only when the record shown is not of the current year', () => {
    expect(recordYearNote({ recordYearId: 2, recordYear: '2025-26' }, 1)).toBe('2025-26');
    expect(recordYearNote({ recordYearId: 1, recordYear: '2026-27' }, 1)).toBeNull();
  });

  it('shows nothing for a student without a record, or when there is no current year to compare with', () => {
    expect(recordYearNote({ recordYearId: null, recordYear: null }, 1)).toBeNull();
    expect(recordYearNote({}, 1)).toBeNull();
    expect(recordYearNote({ recordYearId: 2, recordYear: '2025-26' }, null)).toBe('2025-26');
  });
});

describe('feeText (FR-010, FR-011)', () => {
  const money = (value: number) => value.toFixed(2);

  it('shows the amount with two decimals when the student has a fee', () => {
    expect(feeText({ totalFee: 28000 }, money)).toBe('28000.00');
    expect(feeText({ totalFee: 0 }, money)).toBe('0.00');
  });

  it('says Fee not set when the account has no amount', () => {
    expect(feeText({ totalFee: null, feeState: 'Fee not set' }, money)).toBe('Fee not set');
  });

  it('says Not finalised when there is no account', () => {
    expect(feeText({ totalFee: null, feeState: null }, money)).toBe('Not finalised');
  });

  it('sorts and filters Total fee as a number and keeps Fee state as a hidden column', () => {
    const columns = studentColumns(null);
    expect(columns.find((column) => column.field === 'totalFee')?.type).toBe('number');
    expect(columns.find((column) => column.field === 'feeState')?.hidden).toBe(true);
  });
});
