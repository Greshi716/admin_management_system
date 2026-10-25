import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Button } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { AuthStore } from '../../core/auth/auth.store';
import { SIGN_IN_ROUTE } from '../../core/auth/auth.guard';
import { ChangePasswordDialogComponent } from '../auth/change-password/change-password-dialog.component';

/**
 * The page shown after sign-in. User Story 6 replaces it with the real app shell (sidebar, top bar, user menu);
 * until then it carries the two things sign-in needs: the reminder popup to change the default password
 * (FR-009) and Log out (FR-006).
 */
@Component({
  selector: 'app-home',
  imports: [Button, Dialog, RouterLink, ChangePasswordDialogComponent],
  templateUrl: './home.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomeComponent {
  protected readonly store = inject(AuthStore);
  private readonly router = inject(Router);

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
