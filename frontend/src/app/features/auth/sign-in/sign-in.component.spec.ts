import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthStore } from '../../../core/auth/auth.store';
import { SignInComponent } from './sign-in.component';

const LOGIN = '/api/auth/login';

describe('SignInComponent (FR-001, FR-004, FR-044)', () => {
  let fixture: ComponentFixture<SignInComponent>;
  let controller: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    controller = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture = TestBed.createComponent(SignInComponent);
    fixture.detectChanges();
    controller.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
  });

  afterEach(() => {
    vi.useRealTimers();
    controller.verify();
  });

  const el = () => fixture.nativeElement as HTMLElement;
  const input = (id: string) => el().querySelector(`#${id}`) as HTMLInputElement;
  const submitButton = () => el().querySelector('button[type="submit"]') as HTMLButtonElement;

  function type(username: string, password: string): void {
    input('username').value = username;
    input('username').dispatchEvent(new Event('input'));
    input('password').value = password;
    input('password').dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function submit(): void {
    submitButton().click();
    fixture.detectChanges();
  }

  it('asks for both fields before calling the server', () => {
    submit();

    expect(el().textContent).toContain('Enter your username.');
    expect(el().textContent).toContain('Enter your password.');
    controller.expectNone(LOGIN);
  });

  it('signs in and goes to the home page', () => {
    type('testadmin', 'secret-pass-1');
    submit();

    const request = controller.expectOne(LOGIN);
    expect(request.request.body).toEqual({ username: 'testadmin', password: 'secret-pass-1' });
    request.flush({ name: 'T', username: 'testadmin', role: 'ADMIN', passwordChanged: true, currentAcademicYear: null, idleTimeoutSeconds: 600 });

    expect(TestBed.inject(AuthStore).signedIn()).toBe(true);
    expect(navigate).toHaveBeenCalledWith('/');
  });

  it('shows the generic message for wrong credentials and empties the password', () => {
    type('testadmin', 'wrong-password');
    submit();

    controller.expectOne(LOGIN).flush(
      { status: 401, detail: 'The username or password is not correct.' },
      { status: 401, statusText: 'Unauthorized' },
    );
    fixture.detectChanges();

    expect(el().querySelector('[data-testid="wrong-credentials"]')?.textContent).toContain(
      'The username or password is not correct.',
    );
    expect(input('password').value).toBe('');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('shows the pause, the retry time and a disabled button, then enables it again', () => {
    vi.useFakeTimers();
    type('testadmin', 'wrong-password');
    submit();

    controller.expectOne(LOGIN).flush(
      { status: 423, detail: 'Too many failed sign-ins. Try again in 5 minutes.', retryAfterSeconds: 300 },
      { status: 423, statusText: 'Locked' },
    );
    fixture.detectChanges();

    expect(el().querySelector('[data-testid="paused-notice"]')?.textContent).toContain('Try again in 5 minutes.');
    expect(el().querySelector('[data-testid="paused-notice"]')?.textContent).toContain('You can try again at');
    expect(submitButton().disabled).toBe(true);

    vi.advanceTimersByTime(301_000);
    fixture.detectChanges();

    expect(el().querySelector('[data-testid="paused-notice"]')).toBeNull();
    expect(submitButton().disabled).toBe(false);
  });

  it('shows why the admin was sent here after the session ended', () => {
    TestBed.inject(AuthStore).notice.set('You were signed out after 10 minutes without activity.');
    fixture.detectChanges();

    expect(el().querySelector('[data-testid="session-notice"]')?.textContent).toContain('10 minutes');
  });

  it('can show and hide the password', () => {
    expect(input('password').type).toBe('password');

    (el().querySelector('p-button[type="button"] button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(input('password').type).toBe('text');
  });

  it('tells the admin to contact the developer about a forgotten password', () => {
    expect(el().textContent).toContain('Forgot your password? Contact the developer.');
  });
});
