import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthStore } from '../../core/auth/auth.store';

interface SetupItem {
  label: string;
  link: string;
  /** Stays open while today falls in no academic year (FR-013). */
  alwaysOpen?: boolean;
}

interface SetupGroup {
  title: string;
  items: SetupItem[];
}

/** Setup (FR-014): a left list of the master data screens and the audit log, with the chosen screen beside it. */
@Component({
  selector: 'app-setup-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="grid gap-6 md:grid-cols-[14rem_minmax(0,1fr)]">
      <nav aria-label="Setup" class="flex flex-row gap-4 overflow-x-auto md:flex-col md:gap-6">
        @for (group of groups; track group.title) {
          <div>
            <p class="mb-1 px-3 text-xs font-bold uppercase tracking-wider text-muted">{{ group.title }}</p>
            <ul class="m-0 flex list-none gap-1 p-0 md:flex-col">
              @for (item of group.items; track item.link) {
                <li>
                  @if (closed(item)) {
                    <span
                      aria-disabled="true"
                      title="Create an academic year covering today first"
                      class="block cursor-not-allowed whitespace-nowrap rounded-control px-3 py-2 text-[0.95rem] text-muted opacity-60"
                      >{{ item.label }}</span
                    >
                  } @else {
                    <a
                      [routerLink]="item.link"
                      routerLinkActive="bg-accent-soft font-semibold text-accent-text"
                      class="block whitespace-nowrap rounded-control px-3 py-2 text-[0.95rem] text-ink no-underline transition-colors hover:bg-accent-soft"
                      >{{ item.label }}</a
                    >
                  }
                </li>
              }
            </ul>
          </div>
        }
      </nav>
      <div class="min-w-0">
        <router-outlet />
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SetupLayoutComponent {
  private readonly store = inject(AuthStore);

  protected readonly groups: SetupGroup[] = [
    {
      title: 'Master data',
      items: [
        { label: 'Academic years', link: '/setup/academic-years', alwaysOpen: true },
        { label: 'Standards', link: '/setup/standards' },
        { label: 'Divisions', link: '/setup/divisions' },
        { label: 'Student categories', link: '/setup/student-categories' },
      ],
    },
    { title: 'System', items: [{ label: 'Audit log', link: '/setup/audit-log' }] },
  ];

  protected closed(item: SetupItem): boolean {
    return this.store.noCurrentYear() && !item.alwaysOpen;
  }
}
