import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CampusTableComponent } from '../../../shared/table/campus-table.component';
import { CampusColumn, TableRequest } from '../../../shared/table/table-types';
import { formatMoney } from '../../../shared/ui/money';
import { PageSkeletonComponent } from '../../../shared/ui/page-skeleton.component';
import { StudentsService } from '../../students/students.service';
import { formatDate } from '../finance-format';
import { DayTotal, ReceiptsService } from '../receipts.service';
import { MONEY_FIELDS, registerColumns, registerStatusClass } from './receipt-columns';

/**
 * The receipt register (FR-019, FR-020): every receipt, newest first, with the date range, Mode, Standard and Status
 * filters, a search by receipt number or student, a row link to the receipt, and Export to Excel through the shared
 * table. Beside it the day-wise totals of the same filters, as the server adds them up (valid receipts only); the
 * browser adds nothing.
 */
@Component({
  selector: 'app-receipt-list',
  imports: [RouterLink, CampusTableComponent, PageSkeletonComponent],
  template: `
    <div class="mb-6 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 class="m-0 text-3xl font-bold tracking-tight">Receipts</h1>
        <p class="mt-1 text-muted">Every receipt, newest first. Open one to see it or to print it.</p>
      </div>
      <a routerLink="/finance/fee-collection" class="btn btn-primary">Collect fee</a>
    </div>

    @if (columns(); as shownColumns) {
      <campus-table
        endpoint="/api/receipts"
        [columns]="shownColumns"
        searchPlaceholder="Receipt no. or student"
        (requested)="filtersChanged($event)"
      >
        <ng-template #cell let-row let-column="column">
          @if (column.field === 'status') {
            <span class="rounded-full px-2 py-0.5 text-xs font-semibold" [class]="statusClass(row['status'])" data-testid="receipt-status">{{ row['status'] }}</span>
          } @else {
            <span class="tabular-nums" [attr.data-testid]="'amount-' + column.field">{{ amount(row, column.field) }}</span>
          }
        </ng-template>
        <ng-template #rowActions let-row>
          <a [routerLink]="['/finance/receipts', row['serialNo']]" class="btn btn-secondary btn-outlined btn-sm" data-testid="open-receipt">Open</a>
        </ng-template>
      </campus-table>

      <section class="mt-6 rounded-card border border-border bg-surface p-4" aria-labelledby="day-totals-heading" data-testid="day-totals">
        <h2 id="day-totals-heading" class="mb-1 text-lg font-bold">Day-wise totals</h2>
        <p class="mb-3 text-sm text-muted">Valid receipts of the filters above. A cheque marked not cleared is not counted.</p>
        @if (totalsFailed()) {
          <p role="alert" class="text-error-text">The totals could not be loaded.</p>
        } @else if (totals().length === 0) {
          <p class="text-muted" data-testid="day-totals-empty">No valid receipts for these filters.</p>
        } @else {
          <div class="overflow-x-auto">
            <table class="w-full text-left">
              <thead>
                <tr class="text-sm text-muted">
                  <th class="py-1 pr-4 font-semibold">Date</th>
                  <th class="py-1 pr-4 text-right font-semibold">Receipts</th>
                  <th class="py-1 pr-4 text-right font-semibold">Cash</th>
                  <th class="py-1 pr-4 text-right font-semibold">Cheque</th>
                  <th class="py-1 text-right font-semibold">Total</th>
                </tr>
              </thead>
              <tbody>
                @for (day of totals(); track day.date) {
                  <tr data-testid="day-total-row">
                    <td class="py-1 pr-4">{{ date(day.date) }}</td>
                    <td class="py-1 pr-4 text-right tabular-nums" data-testid="day-count">{{ day.count }}</td>
                    <td class="py-1 pr-4 text-right tabular-nums" data-testid="day-cash">{{ money(day.cash) }}</td>
                    <td class="py-1 pr-4 text-right tabular-nums" data-testid="day-cheque">{{ money(day.cheque) }}</td>
                    <td class="py-1 text-right tabular-nums font-semibold" data-testid="day-total">{{ money(day.total) }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </section>
    } @else {
      <app-page-skeleton [title]="false" />
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReceiptListComponent implements OnInit {
  private readonly students = inject(StudentsService);
  private readonly receipts = inject(ReceiptsService);

  protected readonly columns = signal<CampusColumn[] | null>(null);
  protected readonly totals = signal<DayTotal[]>([]);
  protected readonly totalsFailed = signal(false);

  protected readonly money = formatMoney;
  protected readonly date = formatDate;
  protected readonly statusClass = registerStatusClass;

  /** The filters the totals were last asked for; paging and sorting do not change them. */
  private asked = '';

  ngOnInit(): void {
    this.students.filterOptions().subscribe({
      next: (options) => this.columns.set(registerColumns(options)),
      error: () => this.columns.set(registerColumns(null)), // the error interceptor has shown the reason
    });
  }

  /** A page of the list loaded: ask for the totals again only when the search or a filter is not the same as before. */
  protected filtersChanged(request: TableRequest): void {
    const key = JSON.stringify({ search: request.globalFilter ?? '', filters: request.filters });
    if (key === this.asked) {
      return;
    }
    this.asked = key;
    this.receipts.dayTotals({ first: 0, rows: 10, globalFilter: request.globalFilter, filters: request.filters }).subscribe({
      next: (days) => {
        this.totalsFailed.set(false);
        this.totals.set(days);
      },
      error: () => {
        this.asked = ''; // try again on the next page load
        this.totalsFailed.set(true);
      },
    });
  }

  protected amount(row: Record<string, unknown>, field: string): string {
    const value = row[field];
    return MONEY_FIELDS.includes(field) && typeof value === 'number' ? formatMoney(value) : '—';
  }
}
