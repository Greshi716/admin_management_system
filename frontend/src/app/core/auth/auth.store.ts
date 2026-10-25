import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { catchError, finalize, map, Observable, of, tap } from 'rxjs';
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
      catchError(() => {
        this.clear();
        return of(false);
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
