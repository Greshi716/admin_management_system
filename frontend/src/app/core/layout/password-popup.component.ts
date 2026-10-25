import { ChangeDetectionStrategy, Component, inject, output, signal } from '@angular/core';
import { DialogComponent } from '../../shared/ui/dialog.component';
import { AuthStore } from '../auth/auth.store';

/**
 * After sign-in, while the password is still the one that was made for the admin, a popup asks to change it with
 * "Change now" and "Later". Nothing is forced: "Later" closes it, and it comes back at the next sign-in. It is not
 * shown once the password is changed (FR-009).
 */
@Component({
  selector: 'app-password-popup',
  imports: [DialogComponent],
  template: `
    <app-dialog
      header="Change your password"
      [closable]="false"
      [visible]="open()"
      (visibleChange)="open.set($event)"
      width="26rem"
    >
      <p class="mb-4">You are using the password that was made for you. Please choose your own.</p>
      <div class="flex justify-end gap-2">
        <button type="button" (click)="open.set(false)" class="btn btn-secondary btn-outlined">Later</button>
        <button type="button" (click)="changeNow()" class="btn btn-primary">Change now</button>
      </div>
    </app-dialog>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PasswordPopupComponent {
  /** The admin chose "Change now"; the shell opens the change-password dialog. */
  readonly changeRequested = output<void>();

  protected readonly open = signal(!inject(AuthStore).passwordChanged());

  protected changeNow(): void {
    this.open.set(false);
    this.changeRequested.emit();
  }
}
