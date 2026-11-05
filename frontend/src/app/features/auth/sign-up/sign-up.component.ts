import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthStore } from '../../../core/auth/auth.store';
import { problemOf } from '../../../core/http/api-problem';

type Field = 'name' | 'username' | 'password' | 'confirmPassword';

/**
 * Demo-only "Create account" page (F6 FR-001 to FR-008). When the server has signup off, the page sends the
 * visitor back to sign-in. After a successful sign-up the visitor is already signed in and goes to the home page.
 */
@Component({
  selector: 'app-sign-up',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './sign-up.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SignUpComponent {
  private readonly store = inject(AuthStore);
  private readonly router = inject(Router);

  protected readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    username: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(8)] }),
    confirmPassword: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  protected readonly busy = signal(false);
  protected readonly showPassword = signal(false);
  /** Messages from the server per field, for example "This username is taken." */
  protected readonly serverErrors = signal<Partial<Record<Field, string>>>({});

  constructor() {
    this.store.signupEnabled().subscribe((enabled) => {
      if (!enabled) {
        void this.router.navigateByUrl('/sign-in');
      }
    });
  }

  protected toggleShowPassword(): void {
    this.showPassword.update((shown) => !shown);
  }

  protected error(field: Field): string | null {
    const server = this.serverErrors()[field];
    if (server) {
      return server;
    }
    const control = this.form.controls[field];
    if (!(control.touched || control.dirty)) {
      return null;
    }
    if (control.hasError('required')) {
      return { name: 'Enter your name.', username: 'Choose a username.', password: 'Choose a password.', confirmPassword: 'Enter the password again.' }[field];
    }
    if (control.hasError('minlength')) {
      return 'Use at least 8 characters.';
    }
    if (field === 'confirmPassword' && control.value !== this.form.controls.password.value) {
      return 'The passwords do not match.';
    }
    return null;
  }

  protected submit(): void {
    if (this.busy()) {
      return;
    }
    this.serverErrors.set({});
    const { password, confirmPassword } = this.form.getRawValue();
    if (this.form.invalid || password !== confirmPassword) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    const { name, username } = this.form.getRawValue();
    this.store.signup({ name: name.trim(), username: username.trim(), password, confirmPassword }).subscribe({
      next: () => {
        this.busy.set(false);
        void this.router.navigateByUrl('/');
      },
      error: (error: unknown) => {
        this.busy.set(false);
        if (error instanceof HttpErrorResponse && error.status === 422) {
          const errors: Partial<Record<Field, string>> = {};
          for (const fieldError of problemOf(error.error)?.fieldErrors ?? []) {
            errors[fieldError.field as Field] = fieldError.message;
          }
          this.serverErrors.set(errors);
        }
        // Other failures were already shown as a toast by the error interceptor.
      },
    });
  }
}
