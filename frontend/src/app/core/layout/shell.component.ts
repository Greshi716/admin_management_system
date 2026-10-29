import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ChangePasswordDialogComponent } from '../../features/auth/change-password/change-password-dialog.component';
import { PageSkeletonComponent } from '../../shared/ui/page-skeleton.component';
import { SIGN_IN_ROUTE } from '../auth/auth.guard';
import { AuthStore } from '../auth/auth.store';
import { NoCurrentYearNoticeComponent } from './no-current-year.notice';
import { PasswordPopupComponent } from './password-popup.component';

interface Tab {
  label: string;
  link: string;
  /** Stays open while today falls in no academic year (FR-013). */
  alwaysOpen?: boolean;
}

/**
 * The frame around every signed-in screen (FR-014, FR-028): the top bar with the logo, the tabs of the modules
 * that are built (Students, Setup), the current academic year, the user's name, Change password and Log out, and
 * the reminder popup that asks the admin to change the default password until it is changed (FR-006, FR-009).
 * While today falls in no academic year it shows the blocking notice and closes every tab except Setup, whose
 * list in turn closes everything except Academic years (FR-013). Modules that are not built yet get no tab.
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

  protected readonly tabs: Tab[] = [
    { label: 'Students', link: '/students' },
    { label: 'Finance', link: '/finance' },
    { label: 'Setup', link: '/setup', alwaysOpen: true },
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

  protected closed(item: Tab): boolean {
    return this.store.noCurrentYear() && !item.alwaysOpen;
  }

  protected logout(): void {
    const done = () => void this.router.navigateByUrl(SIGN_IN_ROUTE);
    this.store.logout().subscribe({ next: done, error: done });
  }
}
