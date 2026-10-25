import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { IdleWatchService } from '../auth/idle-watch.service';
import { ToastService } from '../toast/toast.service';
import { ApiProblem, problemOf, STALE_VERSION } from './api-problem';

const LOGIN_URL = '/api/auth/login';
const ME_URL = '/api/auth/me';

/**
 * Shows a toast for every failed call (FR-039) and hands a lost session to IdleWatchService (FR-008). The
 * error is always passed on unchanged, so a form can still read `fieldErrors` (FR-040) and a `STALE_VERSION`
 * body with the latest record (FR-046).
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const toast = inject(ToastService);
  const idleWatch = inject(IdleWatchService);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse) {
        report(error, req, toast, idleWatch);
      }
      return throwError(() => error);
    }),
  );
};

function report(
  error: HttpErrorResponse,
  req: HttpRequest<unknown>,
  toast: ToastService,
  idleWatch: IdleWatchService,
): void {
  const problem = problemOf(error.error);

  if (error.status === 401) {
    // Sign-in shows its own notice, and the route guard answers for the user check.
    if (!isCall(req, LOGIN_URL) && !isCall(req, ME_URL)) {
      idleWatch.sessionEnded();
    }
    return;
  }

  // A paused sign-in is explained on the sign-in page.
  if (error.status === 423 && isCall(req, LOGIN_URL)) {
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
