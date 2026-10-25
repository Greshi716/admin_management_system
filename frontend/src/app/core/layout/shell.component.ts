import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ChangePasswordDialogComponent } from '../../features/auth/change-password/change-password-dialog.component';
import { PageSkeletonComponent } from '../../shared/ui/page-skeleton.component';
import { SIGN_IN_ROUTE } from '../auth/auth.guard';
import { AuthStore } from '../auth/auth.store';
import { ACADEMIC_YEARS_ROUTE } from '../auth/current-year.guard';
import { NoCurrentYearNoticeComponent } from './no-current-year.notice';
import { PasswordPopupComponent } from './password-popup.component';

interface NavItem {
  label: string;
  link: string;
  /** Path of a 24x24 outline icon. */
  icon: string;
  /** Stays open while today falls in no academic year (FR-013). */
  alwaysOpen?: boolean;
}

interface NavGroup {
  title: string;
  items: NavItem[];
}

const ICONS = {
  home: 'M3 11l9-8 9 8M5 10v10h5v-6h4v6h5V10',
  calendar: 'M8 2v4M16 2v4M3 10h18M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z',
  layers: 'M12 2l10 5-10 5L2 7l10-5zM2 17l10 5 10-5M2 12l10 5 10-5',
  grid: 'M3 3h7v7H3zM14 3h7v7h-7zM14 14h7v7h-7zM3 14h7v7H3z',
  shield: 'M12 2l8 3v6c0 5-3.5 9-8 11-4.5-2-8-6-8-11V5l8-3zM9 12l2 2 4-4',
  users: 'M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM23 21v-2a4 4 0 0 0-3-3.9M16 3.1a4 4 0 0 1 0 7.8',
  tag: 'M20.6 13.4l-7.2 7.2a2 2 0 0 1-2.8 0L2 12V2h10l8.6 8.6a2 2 0 0 1 0 2.8zM7 7h.01',
};

/**
 * The frame around every signed-in screen (FR-028): sidebar menu, top bar with the user's name, the current
 * academic year, Change password and Log out, and the reminder popup that asks the admin to change the default
 * password until it is changed (FR-006, FR-009). While today falls in no academic year it shows the blocking
 * notice and closes every menu item except Academic years (FR-013). Modules that are not built yet are left out
 * of the menu.
 */
@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    ChangePasswordDialogComponent,
    PasswordPopupComponent,
    NoCurrentYearNoticeComponent,
    PageSkeletonComponent,
  ],
  templateUrl: './shell.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ShellComponent {
  protected readonly store = inject(AuthStore);
  private readonly router = inject(Router);

  protected readonly groups: NavGroup[] = [
    { title: 'Overview', items: [{ label: 'Home', link: '/', icon: ICONS.home }] },
    // Temporary entry until the shell restyle (F1 T024) puts Students on the top bar.
    { title: 'School', items: [{ label: 'Students', link: '/students', icon: ICONS.users }] },
    {
      title: 'Master data',
      items: [
        { label: 'Academic years', link: ACADEMIC_YEARS_ROUTE, icon: ICONS.calendar, alwaysOpen: true },
        { label: 'Standards', link: '/master-data/standards', icon: ICONS.layers },
        { label: 'Divisions', link: '/master-data/divisions', icon: ICONS.grid },
        { label: 'Student categories', link: '/master-data/student-categories', icon: ICONS.tag },
      ],
    },
    { title: 'System', items: [{ label: 'Audit log', link: '/audit-log', icon: ICONS.shield }] },
  ];

  protected readonly initials = computed(() => {
    const name = this.store.user()?.name?.trim() ?? '';
    return (
      name
        .split(/\s+/)
        .map((part) => part[0])
        .join('')
        .slice(0, 2)
        .toUpperCase() || 'A'
    );
  });

  /** True while a screen is being opened, so the content area shows a placeholder instead of the old screen. */
  protected readonly navigating = computed(() => this.router.currentNavigation() !== null);
  protected readonly changeOpen = signal(false);

  protected closed(item: NavItem): boolean {
    return this.store.noCurrentYear() && !item.alwaysOpen;
  }

  protected logout(): void {
    const done = () => void this.router.navigateByUrl(SIGN_IN_ROUTE);
    this.store.logout().subscribe({ next: done, error: done });
  }
}
