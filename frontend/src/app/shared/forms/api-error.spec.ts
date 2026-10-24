import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { applyFieldErrors, serverMessage } from './api-error';

// FR-040
describe('applyFieldErrors', () => {
  function response(body: object | null, status = 422): HttpErrorResponse {
    return new HttpErrorResponse({ status, error: body });
  }

  function yearForm(): FormGroup {
    return new FormGroup({
      name: new FormControl('2026-27', Validators.required),
      startDate: new FormControl('2026-04-01'),
      endDate: new FormControl('2027-03-31'),
    });
  }

  it('puts each field error on its control and marks it touched', () => {
    const form = yearForm();

    const unmatched = applyFieldErrors(
      form,
      response({
        fieldErrors: [
          { field: 'name', message: 'An academic year with this name already exists.' },
          { field: 'startDate', message: 'These dates overlap another academic year.' },
        ],
      }),
    );

    expect(unmatched).toEqual([]);
    expect(serverMessage(form.get('name'))).toBe('An academic year with this name already exists.');
    expect(serverMessage(form.get('startDate'))).toBe('These dates overlap another academic year.');
    expect(form.get('name')?.touched).toBe(true);
    expect(form.get('name')?.invalid).toBe(true);
    expect(serverMessage(form.get('endDate'))).toBeNull();
  });

  it('keeps the control’s other errors', () => {
    const form = yearForm();
    form.get('name')?.setValue('');

    applyFieldErrors(form, response({ fieldErrors: [{ field: 'name', message: 'Taken.' }] }));

    expect(form.get('name')?.errors).toEqual({ required: true, server: 'Taken.' });
  });

  it('removes the server message as soon as the admin edits the field', () => {
    const form = yearForm();
    applyFieldErrors(form, response({ fieldErrors: [{ field: 'name', message: 'Taken.' }] }));

    form.get('name')?.setValue('2027-28');

    expect(serverMessage(form.get('name'))).toBeNull();
    expect(form.get('name')?.valid).toBe(true);
  });

  it('removes only the server message when other errors remain', () => {
    const form = yearForm();
    applyFieldErrors(form, response({ fieldErrors: [{ field: 'name', message: 'Taken.' }] }));

    form.get('name')?.setValue('');

    expect(form.get('name')?.errors).toEqual({ required: true });
  });

  it('returns the errors that match no control', () => {
    const form = yearForm();

    const unmatched = applyFieldErrors(form, response({ fieldErrors: [{ field: 'nope', message: 'Unknown.' }] }));

    expect(unmatched).toEqual([{ field: 'nope', message: 'Unknown.' }]);
  });

  it('finds controls in nested groups by dotted path', () => {
    const form = new FormGroup({ address: new FormGroup({ city: new FormControl('') }) });

    applyFieldErrors(form, response({ fieldErrors: [{ field: 'address.city', message: 'Required.' }] }));

    expect(serverMessage(form.get('address.city'))).toBe('Required.');
  });

  it('does nothing for errors without field errors or that are not HTTP errors', () => {
    const form = yearForm();

    expect(applyFieldErrors(form, response({ detail: 'Conflict' }, 409))).toEqual([]);
    expect(applyFieldErrors(form, response(null, 500))).toEqual([]);
    expect(applyFieldErrors(form, new Error('boom'))).toEqual([]);
    expect(form.valid).toBe(true);
  });
});
