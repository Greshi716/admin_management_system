import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

/**
 * Finance (F3): one pill, Fee collection, with the chosen screen below it. Fee tracker, Payroll, Expenses and fund,
 * Dashboard, Approvals, Staff and Reports are not built yet and get no pill.
 */
@Component({
  selector: 'app-finance-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <nav aria-label="Finance" class="mb-6 flex gap-2">
      <a
        routerLink="/finance/fee-collection"
        routerLinkActive="bg-accent text-on-accent font-semibold"
        class="rounded-full border border-border px-4 py-2 text-[0.95rem] text-ink no-underline transition-colors hover:bg-accent-soft"
        >Fee collection</a
      >
    </nav>
    <router-outlet />
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FinanceLayoutComponent {}
