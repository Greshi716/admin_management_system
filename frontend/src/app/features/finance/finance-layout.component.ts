import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

/**
 * Finance (F3): one pill, Fee collection, with its two views below it: Collect fee and the Receipts register.
 * Fee tracker, Payroll, Expenses and fund, Dashboard, Approvals, Staff and Reports are not built yet and get no pill.
 */
@Component({
  selector: 'app-finance-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <nav aria-label="Finance" class="mb-3 flex gap-2">
      <a
        routerLink="/finance/fee-collection"
        class="rounded-full border border-border bg-accent px-4 py-2 text-[0.95rem] font-semibold text-on-accent no-underline"
        >Fee collection</a
      >
    </nav>
    <nav aria-label="Fee collection views" class="mb-6 flex gap-4 border-b border-border">
      <a
        routerLink="/finance/fee-collection"
        routerLinkActive="border-accent font-semibold"
        class="-mb-px border-b-2 border-transparent px-1 pb-2 text-ink no-underline"
        data-testid="view-collect"
        >Collect fee</a
      >
      <a
        routerLink="/finance/receipts"
        routerLinkActive="border-accent font-semibold"
        [routerLinkActiveOptions]="{ exact: true }"
        class="-mb-px border-b-2 border-transparent px-1 pb-2 text-ink no-underline"
        data-testid="view-receipts"
        >Receipts</a
      >
    </nav>
    <router-outlet />
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FinanceLayoutComponent {}
