import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { ToastService } from '../toast/toast.service';
import { ApiProblem, problemOf, STALE_VERSION } from './api-problem';

const LOGIN_URL = '/api/auth/login';
const ME_URL = '/api/auth/me';
const SIGN_IN_ROUTE = '/login';

/**
 * Shows a toast for every failed call (FR-039) and sends the admin to sign-in when the session is gone. The
 * error is always passed on unchanged, so a form can still read `fieldErrors` (FR-040) and a `STALE_VERSION`
 * body with the latest record (FR-046).
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const toast = inject(ToastService);
  const router = inject(Router);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse) {
        report(error, req, toast, router);
      }
      return throwError(() => error);
    }),
  );
};

function report(error: HttpErrorResponse, req: HttpRequest<unknown>, toast: ToastService, router: Router): void {
  const problem = problemOf(error.error);

  if (error.status === 401) {
    if (isCall(req, LOGIN_URL)) {
      toast.error(problem?.detail ?? 'The username or password is not right.', 'Could not sign in');
      return;
    }
    if (!isCall(req, ME_URL)) {
      toast.warn('Your session has ended. Please sign in again.', 'Signed out');
    }
    redirectToSignIn(router);
    return;
  }

  const message = messageFor(error, problem);
  if (problem?.code === STALE_VERSION || error.status === 409 || error.status === 422) {
    toast.warn(message);
  } else {
    toast.error(message);
  }
}

function messageFor(error: HttpErrorResponse, problem: ApiProblem | null): string {
  if (error.status === 0) {
    return 'Cannot reach the server. Check that CampusFlow is running and try again.';
  }
  if (problem?.detail) {
    return problem.detail;
  }
  switch (error.status) {
    case 403:
      return 'You are not allowed to do that.';
    case 404:
      return 'That record was not found. It may have been removed.';
    case 409:
      return 'This change conflicts with existing data.';
    case 422:
      return 'Some fields need to be fixed.';
    default:
      return error.status >= 500
        ? 'The server had a problem. Please try again.'
        : 'The request could not be completed.';
  }
}

function isCall(req: HttpRequest<unknown>, path: string): boolean {
  return req.url.split('?')[0].endsWith(path);
}

function redirectToSignIn(router: Router): void {
  if (!router.url.startsWith(SIGN_IN_ROUTE)) {
    // The route is added with the sign-in screen; until then a failed navigation is not an error to show.
    router.navigateByUrl(SIGN_IN_ROUTE).catch(() => undefined);
  }
}
