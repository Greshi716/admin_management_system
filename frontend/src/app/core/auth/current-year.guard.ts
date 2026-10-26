import { inject } from '@angular/core';
import { CanActivateChildFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthStore } from './auth.store';

export const ACADEMIC_YEARS_ROUTE = '/setup/academic-years';

/**
 * While today falls in no academic year, only the Academic years screen opens; every other screen goes back to it
 * until a year covering today is saved (FR-013). Change password and Log out are not routes, so they keep working.
 */
export const currentYearGuard: CanActivateChildFn = (_route, state) => {
  const router = inject(Router);
  const store = inject(AuthStore);
  return store.ensureLoaded().pipe(
    map((signedIn) => {
      if (!signedIn || !store.noCurrentYear()) {
        return true; // not signed in is the sign-in guard's business
      }
      return state.url.split(/[?#]/)[0] === ACADEMIC_YEARS_ROUTE || router.createUrlTree([ACADEMIC_YEARS_ROUTE]);
    }),
    catchError(() => of(true)), // the session check failed; the sign-in guard has already refused or shown the error
  );
};
