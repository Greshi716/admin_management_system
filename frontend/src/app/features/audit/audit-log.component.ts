import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { CampusTableComponent } from '../../shared/table/campus-table.component';
import { CampusColumn } from '../../shared/table/table-types';

/** What one audit entry changed, as the server stores it: old and new values by field. */
interface ChangeSet {
  old?: Record<string, unknown>;
  new?: Record<string, unknown>;
  [other: string]: unknown;
}

/** Areas and actions the audit log knows today; later modules add their own areas here (FR-027). */
const AREAS = ['Security', 'Master data', 'Students'];
const ACTIONS = ['Create', 'Edit', 'Delete', 'Login', 'Export', 'Cancel', 'Finalise', 'Import'];

const AREA_BADGE: Record<string, string> = {
  Security: 'bg-badge-blue-bg text-badge-blue-text',
  'Master data': 'bg-badge-amber-bg text-badge-amber-text',
  Students: 'bg-badge-violet-bg text-badge-violet-text',
};
const ACTION_BADGE: Record<string, string> = {
  Create: 'bg-badge-violet-bg text-badge-violet-text',
  Edit: 'bg-badge-blue-bg text-badge-blue-text',
  Login: 'bg-badge-grey-bg text-badge-grey-text',
  Export: 'bg-badge-purple-bg text-badge-purple-text',
};
const DEFAULT_BADGE = 'bg-accent-soft text-accent-text';

/**
 * The audit log (FR-021, FR-022, FR-026): the standard table plus filters for date range, month, year, area and
 * action. It is read-only, there is no way to edit or delete an entry (FR-024).
 */
@Component({
  selector: 'app-audit-log',
  imports: [CampusTableComponent],
  templateUrl: './audit-log.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuditLogComponent {
  protected readonly endpoint = '/api/audit-log';
  protected readonly columns: CampusColumn[] = [
    { field: 'id', header: 'Id', type: 'number', hidden: true },
    { field: 'at', header: 'Date and time', type: 'date' },
    { field: 'user', header: 'User' },
    { field: 'area', header: 'Area', badgeClass: (value) => AREA_BADGE[String(value)] ?? DEFAULT_BADGE },
    { field: 'action', header: 'Action', badgeClass: (value) => ACTION_BADGE[String(value)] ?? DEFAULT_BADGE },
    { field: 'entity', header: 'Record' },
    { field: 'changes', header: 'What changed', lines: (value) => describeChanges(value) },
    { field: 'reason', header: 'Reason' },
  ];
  protected readonly areas = AREAS;
  protected readonly actions = ACTIONS;

  protected readonly from = signal('');
  protected readonly to = signal('');
  protected readonly month = signal('');
  protected readonly year = signal('');
  protected readonly area = signal('');
  protected readonly action = signal('');

  /** Sent with every search and export; empty filters are left out. */
  protected readonly params = computed<Record<string, unknown>>(() => {
    const params: Record<string, unknown> = {};
    const add = (key: string, value: string) => value && (params[key] = value);
    add('from', this.from());
    add('to', this.to());
    add('month', this.month());
    add('area', this.area());
    add('action', this.action());
    if (/^\d{4}$/.test(this.year())) {
      // sent once all four digits are typed, so typing "2026" does not ask the server about "2", "20" and "202"
      params['year'] = Number(this.year());
    }
    return params;
  });

  protected readonly anyFilter = computed(() => Object.keys(this.params()).length > 0);
  protected readonly reversed = computed(() => !!this.from() && !!this.to() && this.from() > this.to());

  protected set(target: { set(value: string): void }, event: Event): void {
    target.set((event.target as HTMLInputElement | HTMLSelectElement).value);
  }

  protected today(): void {
    const day = isoDay(new Date());
    this.range(day, day);
  }

  protected thisMonth(): void {
    const now = new Date();
    this.range(
      isoDay(new Date(now.getFullYear(), now.getMonth(), 1)),
      isoDay(new Date(now.getFullYear(), now.getMonth() + 1, 0)),
    );
  }

  protected thisYear(): void {
    const now = new Date();
    this.range(isoDay(new Date(now.getFullYear(), 0, 1)), isoDay(new Date(now.getFullYear(), 11, 31)));
  }

  protected clear(): void {
    for (const filter of [this.from, this.to, this.month, this.year, this.area, this.action]) {
      filter.set('');
    }
  }

  private range(from: string, to: string): void {
    this.from.set(from);
    this.to.set(to);
  }
}

/** Turns the stored changes into one line per field, for example "name: 2025-26 → 2026-27". */
export function describeChanges(raw: unknown): string[] {
  if (typeof raw !== 'string' || raw === '') {
    return [];
  }
  let changes: ChangeSet;
  try {
    changes = JSON.parse(raw) as ChangeSet;
  } catch {
    return [raw];
  }
  const lines: string[] = [];
  const before = changes.old ?? {};
  const after = changes.new ?? {};
  for (const field of Object.keys({ ...before, ...after })) {
    const oldValue = field in before ? show(before[field]) : null;
    const newValue = field in after ? show(after[field]) : null;
    lines.push(
      oldValue !== null && newValue !== null ? `${field}: ${oldValue} → ${newValue}` : `${field}: ${newValue ?? oldValue}`,
    );
  }
  for (const [key, value] of Object.entries(changes)) {
    if (key !== 'old' && key !== 'new') {
      lines.push(`${key}: ${show(value)}`);
    }
  }
  return lines;
}

function show(value: unknown): string {
  return value !== null && typeof value === 'object' ? JSON.stringify(value) : String(value);
}

/** yyyy-MM-dd of a local date; toISOString would shift the day in some time zones. */
function isoDay(date: Date): string {
  const two = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${two(date.getMonth() + 1)}-${two(date.getDate())}`;
}
