/** What a list shows for one column. The server declares the same columns (ColumnDef) and exports all of them. */
export interface CampusColumn {
  /** Key in each row and in the request. */
  field: string;
  header: string;
  type?: 'text' | 'number' | 'date' | 'boolean';
  /** Hidden columns are not shown on screen but are still in the Excel export (FR-033). */
  hidden?: boolean;
  /** For a boolean column: show this text as a tag when the value is true, and nothing when it is false. */
  tag?: string;
  /** Shows the value as a coloured badge; returns the Tailwind classes for that value. */
  badgeClass?: (value: unknown) => string;
  /** Shows the value as a short list, one line per item (for example what a change touched). */
  lines?: (value: unknown) => string[];
  /** The cell is drawn by the table's `cell` template (it receives the row and the column), for example a photo. */
  custom?: boolean;
  /** False takes the sort arrow off the heading. */
  sortable?: boolean;
  /** False takes the filter button off the heading. */
  filterable?: boolean;
  /** Replaces the text box of the column's filter by dropdowns that filter on ids (Standard, Division, Category). */
  dropdowns?: DropdownFilter[];
}

/** A filter chosen from a list of ids, sent as an exact match on `field` (for example the standard of a student). */
export interface DropdownFilter {
  /** Request field the chosen value is sent in. */
  field: string;
  label: string;
  options: { value: number | string; label: string }[];
}

export interface TableFilter {
  value: unknown;
  matchMode: string;
}

/** Request body of `{endpoint}/search` and `{endpoint}/export` (contracts/openapi.yaml `TableRequest`). */
export interface TableRequest {
  first: number;
  rows: number;
  sortField?: string;
  /** 1 ascending, -1 descending. */
  sortOrder?: 1 | -1;
  globalFilter?: string;
  filters: Record<string, TableFilter>;
}

export interface TablePage {
  rows: Record<string, unknown>[];
  total: number;
}

export const PAGE_SIZES = [10, 25, 50, 100] as const;
export const DEFAULT_PAGE_SIZE = 25;
export const SEARCH_DEBOUNCE_MS = 300;
