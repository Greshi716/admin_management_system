import { inject, Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { SIGN_IN_ROUTE } from './auth.guard';
import { AuthStore } from './auth.store';

export const SESSION_ENDED_MESSAGE = 'You were signed out after 10 minutes without activity. Please sign in again.';

/**
 * Handles the end of a session (FR-008). The server signs the admin out after 10 minutes without a request, so
 * the app finds out only when a call comes back 401. There is deliberately no timer and no polling here: any
 * request to the API would extend the session and the 10 minutes would never run out.
 */
@Injectable({ providedIn: 'root' })
export class IdleWatchService {
  private readonly store = inject(AuthStore);
  private readonly router = inject(Router);

  /** Called by the error interceptor when a call other than sign-in or the user check answers 401. */
  sessionEnded(): void {
    this.store.clear();
    this.store.notice.set(SESSION_ENDED_MESSAGE);
    if (!this.router.url.startsWith(SIGN_IN_ROUTE)) {
      this.router.navigateByUrl(SIGN_IN_ROUTE).catch(() => undefined);
    }
  }
}
