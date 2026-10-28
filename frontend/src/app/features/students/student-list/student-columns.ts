import { CampusColumn } from '../../../shared/table/table-types';
import { FilterOptions } from '../students.service';

const ACTIVE_BADGE = 'bg-accent-soft text-accent-text';
const LEFT_BADGE = 'bg-page text-muted border border-border';

/**
 * The columns of the Students list (FR-008). The server returns the record shown for each student (the current
 * year's, else the most recent) with the ids of its standard, division and category, which the dropdown filters
 * use. The filter options hold every value, active or not and of any year, because a student keeps the values of
 * its record. Aadhaar and religion/caste are not columns of the screen at all: they are masked in the Excel export only,
 * and the server refuses to search, filter or sort by them (FR-010).
 */
export function studentColumns(options: FilterOptions | null): CampusColumn[] {
  const standards = options?.standards ?? [];
  const standardName = new Map(standards.map((standard) => [standard.id, standard.name]));
  return [
    { field: 'id', header: 'Id', type: 'number', hidden: true },
    { field: 'photoVersion', header: 'Photo version', type: 'number', hidden: true },
    { field: 'surname', header: 'Surname', hidden: true },
    { field: 'firstName', header: 'First name', hidden: true },
    { field: 'recordYearId', header: 'Record year id', type: 'number', hidden: true },
    { field: 'recordYear', header: 'Record year', hidden: true },
    { field: 'photo', header: 'Photo', custom: true, sortable: false, filterable: false },
    { field: 'grNo', header: 'GR No.' },
    { field: 'name', header: 'Name' },
    {
      field: 'stdDiv',
      header: 'Std / Div',
      custom: true,
      dropdowns: [
        {
          field: 'standardId',
          label: 'Standard',
          options: standards.map((standard) => ({ value: standard.id, label: standard.name })),
        },
        {
          field: 'divisionId',
          label: 'Division',
          options: (options?.divisions ?? []).map((division) => ({
            value: division.id,
            label: `${standardName.get(division.standardId) ?? ''} ${division.name} (${division.yearName})`.trim(),
          })),
        },
      ],
    },
    {
      field: 'category',
      header: 'Category',
      dropdowns: [
        {
          field: 'categoryId',
          label: 'Category',
          options: (options?.categories ?? []).map((category) => ({ value: category.id, label: category.name })),
        },
      ],
    },
    // Fee not set / Not finalised / the amount: drawn by the list's own cell from totalFee and feeState (F2 FR-010).
    { field: 'totalFee', header: 'Total fee', type: 'number', custom: true },
    { field: 'feeState', header: 'Fee state', hidden: true },
    { field: 'status', header: 'Status', filterable: false, badgeClass: (value) => (value === 'Left' ? LEFT_BADGE : ACTIVE_BADGE) },
  ];
}

/**
 * The academic year is shown in small text under Std / Div only when the record shown belongs to another year than
 * the current one, so the admin can see that a student has no record this year (FR-008).
 */
export function recordYearNote(row: Record<string, unknown>, currentYearId: number | null): string | null {
  const year = row['recordYear'];
  const yearId = row['recordYearId'];
  if (typeof year !== 'string' || year === '' || yearId === null || yearId === undefined) {
    return null;
  }
  return yearId === currentYearId ? null : year;
}

/**
 * What the Total fee cell says (F2 FR-010): the amount with two decimals, "Fee not set" when the student's fee account
 * has no amount, "Not finalised" when the year has no account yet. The amount is the server's total, never computed here.
 */
export function feeText(row: Record<string, unknown>, money: (value: number) => string): string {
  const total = row['totalFee'];
  if (typeof total === 'number') {
    return money(total);
  }
  return row['feeState'] === 'Fee not set' ? 'Fee not set' : 'Not finalised';
}
