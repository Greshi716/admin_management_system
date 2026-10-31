import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { IdleWatchService } from '../auth/idle-watch.service';
import { ToastService } from '../toast/toast.service';
import { errorInterceptor } from './error.interceptor';

describe('errorInterceptor (FR-008, FR-039, FR-046)', () => {
  let http: HttpClient;
  let controller: HttpTestingController;
  let toast: { error: ReturnType<typeof vi.fn>; warn: ReturnType<typeof vi.fn> };
  let idleWatch: { sessionEnded: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    toast = { error: vi.fn(), warn: vi.fn() };
    idleWatch = { sessionEnded: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: ToastService, useValue: toast },
        { provide: IdleWatchService, useValue: idleWatch },
      ],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  function fail(url: string, body: object | null, status: number): unknown {
    let caught: unknown;
    http.get(url).subscribe({ error: (e) => (caught = e) });
    controller.expectOne(url).flush(body, { status, statusText: 'x' });
    return caught;
  }

  it('shows the server detail as a toast and still passes the error on', () => {
    const caught = fail('/api/standards/1', { detail: 'This record is in use.', status: 409 }, 409) as {
      error: { detail: string };
    };

    expect(toast.warn).toHaveBeenCalledWith('This record is in use.');
    expect(caught.error.detail).toBe('This record is in use.');
  });

  it('passes a STALE_VERSION body with the latest record through to the caller', () => {
    const body = { code: 'STALE_VERSION', detail: 'Changed by someone else.', current: { id: 3, version: 7 } };

    const caught = fail('/api/academic-years/3', body, 409) as { error: typeof body };

    expect(caught.error.code).toBe('STALE_VERSION');
    expect(caught.error.current).toEqual({ id: 3, version: 7 });
    expect(toast.warn).toHaveBeenCalledWith('Changed by someone else.');
  });

  it('shows a plain message for a 422 and leaves fieldErrors to the form', () => {
    const body = { fieldErrors: [{ field: 'name', message: 'must not be blank' }] };

    const caught = fail('/api/standards', body, 422) as { error: typeof body };

    expect(toast.warn).toHaveBeenCalledWith('Some fields need to be fixed.');
    expect(caught.error.fieldErrors[0].field).toBe('name');
  });

  it('says the server cannot be reached when there is no response', () => {
    http.get('/api/standards').subscribe({ error: () => undefined });
    controller.expectOne('/api/standards').error(new ProgressEvent('error'));

    expect(toast.error).toHaveBeenCalledWith(expect.stringContaining('Cannot reach the server'));
  });

  it('shows a calm message for a server error', () => {
    fail('/api/standards', null, 500);

    expect(toast.error).toHaveBeenCalledWith('The server had a problem. Please try again.');
  });

  it('does not try to read a blob error body', () => {
    http.post('/api/standards/export', {}, { responseType: 'blob' }).subscribe({ error: () => undefined });
    controller.expectOne('/api/standards/export').flush(new Blob(['x']), { status: 404, statusText: 'x' });

    expect(toast.error).toHaveBeenCalledWith('That record was not found. It may have been removed.');
  });

  it('on 401 hands the lost session to IdleWatchService without a toast', () => {
    fail('/api/standards', null, 401);

    expect(idleWatch.sessionEnded).toHaveBeenCalled();
    expect(toast.warn).not.toHaveBeenCalled();
    expect(toast.error).not.toHaveBeenCalled();
  });

  it('on 401 from the current-user check leaves the answer to the route guard', () => {
    fail('/api/auth/me', null, 401);

    expect(idleWatch.sessionEnded).not.toHaveBeenCalled();
    expect(toast.warn).not.toHaveBeenCalled();
    expect(toast.error).not.toHaveBeenCalled();
  });

  it('on 401 from sign-in itself shows no toast, because the sign-in page shows the reason', () => {
    const caught = fail('/api/auth/login', { detail: 'Wrong username or password.' }, 401) as {
      error: { detail: string };
    };

    expect(toast.error).not.toHaveBeenCalled();
    expect(idleWatch.sessionEnded).not.toHaveBeenCalled();
    expect(caught.error.detail).toBe('Wrong username or password.');
  });

  it('on 423 from sign-in shows no toast and passes the retry time on', () => {
    const caught = fail('/api/auth/login', { retryAfterSeconds: 300 }, 423) as {
      error: { retryAfterSeconds: number };
    };

    expect(toast.error).not.toHaveBeenCalled();
    expect(toast.warn).not.toHaveBeenCalled();
    expect(caught.error.retryAfterSeconds).toBe(300);
  });

  it('shows no toast for REVISE_CONFIRMATION_REQUIRED, the fee screen asks in its own dialog (F2 FR-004)', () => {
    const body = { code: 'REVISE_CONFIRMATION_REQUIRED', detail: 'Confirm.', current: { studentCount: 5 } };

    const caught = fail('/api/fee-structures/1/rows', body, 409) as { error: typeof body };

    expect(toast.warn).not.toHaveBeenCalled();
    expect(toast.error).not.toHaveBeenCalled();
    expect(caught.error.current.studentCount).toBe(5);
  });

  it('shows no toast for BALANCE_CHANGED, the Collect fee screen shows its own pop-up (F3 FR-016)', () => {
    const body = { code: 'BALANCE_CHANGED', detail: 'The balance has changed.', current: { balanceVersion: 'x' } };

    const caught = fail('/api/receipts', body, 409) as { error: typeof body };

    expect(toast.warn).not.toHaveBeenCalled();
    expect(toast.error).not.toHaveBeenCalled();
    expect(caught.error.current.balanceVersion).toBe('x');
  });

  it('does not touch successful calls', () => {
    http.get('/api/standards').subscribe();
    controller.expectOne('/api/standards').flush([]);

    expect(toast.error).not.toHaveBeenCalled();
    expect(toast.warn).not.toHaveBeenCalled();
  });
});
