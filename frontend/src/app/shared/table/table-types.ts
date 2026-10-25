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
