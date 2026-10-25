import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, model, signal } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { AuthStore } from '../../../core/auth/auth.store';
import { ToastService } from '../../../core/toast/toast.service';
import { applyFieldErrors, serverMessage } from '../../../shared/forms/api-error';
import { DialogComponent } from '../../../shared/ui/dialog.component';

export const MIN_PASSWORD_LENGTH = 8;

/** Errors on the whole form: the two new passwords differ, or the new one equals the current one. */
function passwordRules(group: AbstractControl): ValidationErrors | null {
  const current = group.get('currentPassword')?.value as string;
  const next = group.get('newPassword')?.value as string;
  const confirm = group.get('confirmPassword')?.value as string;
  if (next && confirm && next !== confirm) {
    return { mismatch: true };
  }
  if (current && next && current === next) {
    return { same: true };
  }
  return null;
}

/**
 * Change-password form in a dialog (FR-009). Used by the reminder popup after sign-in and by the user menu.
 * The new password needs at least 8 characters and must differ from the current one; the server checks the
 * same rules and its messages are shown beside the fields.
 */
@Component({
  selector: 'app-change-password-dialog',
  imports: [ReactiveFormsModule, DialogComponent],
  templateUrl: './change-password-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChangePasswordDialogComponent {
  readonly visible = model(false);

  private readonly http = inject(HttpClient);
  private readonly store = inject(AuthStore);
  private readonly toast = inject(ToastService);

  protected readonly minLength = MIN_PASSWORD_LENGTH;
  protected readonly busy = signal(false);
  protected readonly serverMessage = serverMessage;

  protected readonly form = new FormGroup(
    {
      currentPassword: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      newPassword: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.minLength(MIN_PASSWORD_LENGTH)],
      }),
      confirmPassword: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    },
    { validators: [passwordRules] },
  );

  protected submit(): void {
    if (this.busy()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { currentPassword, newPassword } = this.form.getRawValue();
    this.busy.set(true);
    this.http.post<void>('/api/auth/change-password', { currentPassword, newPassword }).subscribe({
      next: () => {
        this.busy.set(false);
        this.store.passwordWasChanged();
        this.toast.success('Your password was changed.', 'Password changed');
        this.close();
      },
      error: (error: unknown) => {
        this.busy.set(false);
        applyFieldErrors(this.form, error);
      },
    });
  }

  protected close(): void {
    this.form.reset();
    this.visible.set(false);
  }
}
