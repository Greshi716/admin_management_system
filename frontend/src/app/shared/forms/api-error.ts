import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl } from '@angular/forms';
import { ApiProblem, problemOf } from '../../core/http/api-problem';

/** Key under which a server message is stored in a control's `errors`. */
export const SERVER_ERROR = 'server';

/**
 * Puts the `fieldErrors` of a failed save (HTTP 422, or a 409 that names a field) next to the matching form
 * controls, so the admin sees what to fix beside the field (FR-040). A control is matched by the field name,
 * which may be a dotted path into nested groups. Angular re-runs a control's validators when its value
 * changes, which removes the server message as soon as the admin edits that field.
 *
 * @returns the errors that match no control, so the caller can still show them (for example in a toast)
 */
export function applyFieldErrors(form: AbstractControl, error: unknown): { field: string; message: string }[] {
  const problem = problemFrom(error);
  const unmatched: { field: string; message: string }[] = [];
  for (const fieldError of problem?.fieldErrors ?? []) {
    const control = form.get(fieldError.field);
    if (!control) {
      unmatched.push(fieldError);
      continue;
    }
    control.setErrors({ ...control.errors, [SERVER_ERROR]: fieldError.message });
    control.markAsTouched();
  }
  return unmatched;
}

/** The server message for a control, or null; for use in templates next to the field. */
export function serverMessage(control: AbstractControl | null): string | null {
  const message = control?.errors?.[SERVER_ERROR];
  return typeof message === 'string' ? message : null;
}

function problemFrom(error: unknown): ApiProblem | null {
  return error instanceof HttpErrorResponse ? problemOf(error.error) : null;
}
