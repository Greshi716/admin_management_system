import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthStore } from './auth.store';

export const SIGN_IN_ROUTE = '/sign-in';

/** Sends anyone who is not signed in to the sign-in page before any screen shows (FR-007). */
export const authGuard: CanActivateFn = () => {
  const router = inject(Router);
  return inject(AuthStore)
    .ensureLoaded()
    .pipe(map((signedIn) => signedIn || router.createUrlTree([SIGN_IN_ROUTE])));
};
