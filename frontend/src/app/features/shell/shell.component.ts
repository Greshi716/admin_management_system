import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { SIGN_IN_ROUTE } from '../../core/auth/auth.guard';
import { AuthStore } from '../../core/auth/auth.store';
import { ChangePasswordDialogComponent } from '../auth/change-password/change-password-dialog.component';
import { DialogComponent } from '../../shared/ui/dialog.component';

interface NavItem {
  label: string;
  link: string;
  /** Path of a 24x24 outline icon. */
  icon: string;
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
  tag: 'M20.6 13.4l-7.2 7.2a2 2 0 0 1-2.8 0L2 12V2h10l8.6 8.6a2 2 0 0 1 0 2.8zM7 7h.01',
};

/**
 * The frame around every signed-in screen: sidebar, top bar with the user menu, and the reminder popup that asks
 * the admin to change the default password until it is changed (FR-006, FR-009).
 */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, DialogComponent, ChangePasswordDialogComponent],
  templateUrl: './shell.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ShellComponent {
  protected readonly store = inject(AuthStore);
  private readonly router = inject(Router);

  protected readonly groups: NavGroup[] = [
    { title: 'Overview', items: [{ label: 'Home', link: '/', icon: ICONS.home }] },
    {
      title: 'Master data',
      items: [
        { label: 'Academic years', link: '/master-data/academic-years', icon: ICONS.calendar },
        { label: 'Standards', link: '/master-data/standards', icon: ICONS.layers },
        { label: 'Divisions', link: '/master-data/divisions', icon: ICONS.grid },
        { label: 'Student categories', link: '/master-data/student-categories', icon: ICONS.tag },
      ],
    },
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

  /** Shown after every sign-in until the password has been changed. */
  protected readonly reminderOpen = signal(!this.store.passwordChanged());
  protected readonly changeOpen = signal(false);

  protected changeNow(): void {
    this.reminderOpen.set(false);
    this.changeOpen.set(true);
  }

  protected later(): void {
    this.reminderOpen.set(false);
  }

  protected logout(): void {
    const done = () => void this.router.navigateByUrl(SIGN_IN_ROUTE);
    this.store.logout().subscribe({ next: done, error: done });
  }
}
