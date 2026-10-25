import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { Button } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { AuthStore } from '../../../core/auth/auth.store';
import { problemOf } from '../../../core/http/api-problem';

const WRONG_CREDENTIALS = 'The username or password is not correct.';

/**
 * The sign-in page (FR-001, FR-004, FR-044). A wrong sign-in shows one message that does not say which part was
 * wrong. After 5 failures the server pauses sign-in; the page then shows when to try again and disables the
 * button until that time.
 */
@Component({
  selector: 'app-sign-in',
  imports: [ReactiveFormsModule, Button, InputText],
  templateUrl: './sign-in.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SignInComponent {
  private readonly store = inject(AuthStore);
  private readonly router = inject(Router);

  protected readonly form = new FormGroup({
    username: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  protected readonly showPassword = signal(false);
  protected readonly busy = signal(false);
  protected readonly wrongCredentials = signal<string | null>(null);
  protected readonly notice = this.store.notice;

  private readonly pausedUntil = signal<number | null>(null);
  private readonly now = signal(Date.now());
  private resumeTimer: ReturnType<typeof setTimeout> | undefined;

  protected readonly paused = computed(() => {
    const until = this.pausedUntil();
    return until !== null && this.now() < until;
  });

  protected readonly pausedMessage = signal('');

  /** The local time when sign-in works again, for example "02:35 pm". */
  protected readonly retryTime = computed(() => {
    const until = this.pausedUntil();
    return until === null
      ? ''
      : new Date(until).toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', hour12: true });
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => clearTimeout(this.resumeTimer));
    // Asking who is signed in also makes the server set the XSRF-TOKEN cookie that the sign-in POST must send back,
    // and sends an admin who is already signed in straight to the app.
    this.store.ensureLoaded().subscribe((signedIn) => {
      if (signedIn) {
        void this.router.navigateByUrl('/');
      }
    });
  }

  protected toggleShowPassword(): void {
    this.showPassword.update((shown) => !shown);
  }

  protected submit(): void {
    if (this.busy() || this.paused()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { username, password } = this.form.getRawValue();
    this.busy.set(true);
    this.wrongCredentials.set(null);
    this.store.login(username.trim(), password).subscribe({
      next: () => {
        this.busy.set(false);
        void this.router.navigateByUrl('/');
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.form.controls.password.reset('');
        this.showFailure(error);
      },
    });
  }

  private showFailure(error: unknown): void {
    if (!(error instanceof HttpErrorResponse)) {
      return;
    }
    const problem = problemOf(error.error);
    if (error.status === 423 && problem?.retryAfterSeconds) {
      this.pauseFor(problem.retryAfterSeconds, problem.detail);
    } else if (error.status === 401) {
      this.wrongCredentials.set(problem?.detail ?? WRONG_CREDENTIALS);
    }
    // Other failures (server down, server error) were already shown as a toast by the error interceptor.
  }

  private pauseFor(seconds: number, detail: string | undefined): void {
    const until = Date.now() + seconds * 1000;
    this.pausedMessage.set(detail ?? 'Too many failed sign-ins.');
    this.pausedUntil.set(until);
    this.now.set(Date.now());
    clearTimeout(this.resumeTimer);
    // Only re-enables the button on this page; it makes no request, so it cannot extend a session.
    this.resumeTimer = setTimeout(() => this.now.set(Date.now()), seconds * 1000 + 50);
  }
}
