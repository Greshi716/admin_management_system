import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { catchError, finalize, map, Observable, of, tap, throwError } from 'rxjs';
import { Me } from './me';

const ME_URL = '/api/auth/me';
const LOGIN_URL = '/api/auth/login';
const LOGOUT_URL = '/api/auth/logout';
const DEFAULT_IDLE_SECONDS = 600;

/**
 * Who is signed in. The server session is the truth; this holds the last answer so screens and the route guard
 * do not ask again on every navigation (FR-001, FR-007).
 */
@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly http = inject(HttpClient);
  private readonly current = signal<Me | null>(null);
  private readonly asked = signal(false);

  readonly user = this.current.asReadonly();
  readonly signedIn = computed(() => this.current() !== null);
  /** True when there is nothing to remind the admin about (or nobody is signed in). */
  readonly passwordChanged = computed(() => this.current()?.passwordChanged ?? true);
  /** Name of the academic year that contains today, or null when there is none (FR-013). */
  readonly currentYear = computed(() => this.current()?.currentAcademicYear ?? null);
  /** True when someone is signed in and today falls in no academic year: only Academic years is open then. */
  readonly noCurrentYear = computed(() => this.current() !== null && this.currentYear() === null);
  readonly idleTimeoutSeconds = computed(() => this.current()?.idleTimeoutSeconds ?? DEFAULT_IDLE_SECONDS);
  /** A message for the sign-in page, for example after the session ended. */
  readonly notice = signal<string | null>(null);

  /** Asks the server once who is signed in; later calls reuse the answer. Emits whether someone is signed in. */
  ensureLoaded(): Observable<boolean> {
    if (this.asked()) {
      return of(this.signedIn());
    }
    return this.http.get<Me>(ME_URL).pipe(
      map((me) => {
        this.current.set(me);
        this.asked.set(true);
        return true;
      }),
      catchError((error: unknown) => {
        // Only a 401 means "not signed in". A network error or a 500 says nothing about the session, so it is passed
        // on (the error interceptor has shown the toast) and the admin is not sent to the sign-in page.
        if (error instanceof HttpErrorResponse && error.status === 401) {
          this.clear();
          return of(false);
        }
        return throwError(() => error);
      }),
    );
  }

  login(username: string, password: string): Observable<Me> {
    return this.http.post<Me>(LOGIN_URL, { username, password }).pipe(
      tap((me) => {
        this.current.set(me);
        this.asked.set(true);
        this.notice.set(null);
      }),
    );
  }

  /**
   * Asks the server again who is signed in, so the current academic year is up to date after a year was added or
   * deleted. Keeps the last answer when the call fails.
   */
  refresh(): Observable<void> {
    return this.http.get<Me>(ME_URL).pipe(
      tap((me) => this.current.set(me)),
      map(() => undefined),
      catchError(() => of(undefined)),
    );
  }

  /** Ends the server session. The local state is cleared even when the call fails (for example, already expired). */
  logout(): Observable<void> {
    return this.http.post<void>(LOGOUT_URL, null).pipe(finalize(() => this.clear()));
  }

  /** Forgets the signed-in user without calling the server. */
  clear(): void {
    this.current.set(null);
    this.asked.set(true);
  }

  passwordWasChanged(): void {
    const me = this.current();
    if (me) {
      this.current.set({ ...me, passwordChanged: true });
    }
  }
}
