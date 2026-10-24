import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { ToastService } from '../toast/toast.service';
import { errorInterceptor } from './error.interceptor';

describe('errorInterceptor (FR-039, FR-046)', () => {
  let http: HttpClient;
  let controller: HttpTestingController;
  let toast: { error: ReturnType<typeof vi.fn>; warn: ReturnType<typeof vi.fn> };
  let router: { url: string; navigateByUrl: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    toast = { error: vi.fn(), warn: vi.fn() };
    router = { url: '/students', navigateByUrl: vi.fn().mockResolvedValue(true) };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: ToastService, useValue: toast },
        { provide: Router, useValue: router },
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

  it('on 401 tells the admin the session ended and goes to sign-in', () => {
    fail('/api/standards', null, 401);

    expect(toast.warn).toHaveBeenCalledWith('Your session has ended. Please sign in again.', 'Signed out');
    expect(router.navigateByUrl).toHaveBeenCalledWith('/login');
  });

  it('on 401 from the current-user check goes to sign-in without a toast', () => {
    fail('/api/auth/me', null, 401);

    expect(toast.warn).not.toHaveBeenCalled();
    expect(toast.error).not.toHaveBeenCalled();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/login');
  });

  it('on 401 from sign-in itself shows the reason and does not redirect', () => {
    fail('/api/auth/login', { detail: 'Wrong username or password.' }, 401);

    expect(toast.error).toHaveBeenCalledWith('Wrong username or password.', 'Could not sign in');
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('does not redirect again when already on the sign-in screen', () => {
    router.url = '/login';

    fail('/api/standards', null, 401);

    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('survives a redirect to a route that does not exist yet', async () => {
    router.navigateByUrl.mockRejectedValue(new Error('NG04002'));

    fail('/api/standards', null, 401);
    await Promise.resolve();

    expect(router.navigateByUrl).toHaveBeenCalled();
  });

  it('does not touch successful calls', () => {
    http.get('/api/standards').subscribe();
    controller.expectOne('/api/standards').flush([]);

    expect(toast.error).not.toHaveBeenCalled();
    expect(toast.warn).not.toHaveBeenCalled();
  });
});
