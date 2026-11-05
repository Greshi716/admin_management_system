import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthStore } from '../../../core/auth/auth.store';
import { SignUpComponent } from './sign-up.component';

const SIGNUP = '/api/auth/signup';

describe('SignUpComponent (F6 FR-001 to FR-008)', () => {
  let fixture: ComponentFixture<SignUpComponent>;
  let controller: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  function create(signupEnabled: boolean): void {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    controller = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture = TestBed.createComponent(SignUpComponent);
    fixture.detectChanges();
    controller.expectOne('/api/auth/config').flush({ signupEnabled });
    fixture.detectChanges();
  }

  afterEach(() => controller.verify());

  const el = () => fixture.nativeElement as HTMLElement;
  const input = (id: string) => el().querySelector(`#${id}`) as HTMLInputElement;

  function fill(values: Record<string, string>): void {
    for (const [id, value] of Object.entries(values)) {
      input(id).value = value;
      input(id).dispatchEvent(new Event('input'));
    }
    fixture.detectChanges();
  }

  function submit(): void {
    (el().querySelector('button[type="submit"]') as HTMLButtonElement).click();
    fixture.detectChanges();
  }

  it('goes back to sign-in when signup is off', () => {
    create(false);

    expect(navigate).toHaveBeenCalledWith('/sign-in');
  });

  it('asks for every field and a matching password before calling the server', () => {
    create(true);

    submit();
    expect(el().textContent).toContain('Enter your name.');
    expect(el().textContent).toContain('Choose a username.');

    fill({ name: 'Demo', username: 'demo1', password: 'demo-pass-123', confirmPassword: 'other-pass-123' });
    submit();
    expect(el().textContent).toContain('The passwords do not match.');
    controller.expectNone(SIGNUP);
  });

  it('creates the account, signs in and goes to the home page', () => {
    create(true);
    fill({ name: ' Demo ', username: ' demo1 ', password: 'demo-pass-123', confirmPassword: 'demo-pass-123' });
    submit();

    const request = controller.expectOne(SIGNUP);
    expect(request.request.body).toEqual({
      name: 'Demo',
      username: 'demo1',
      password: 'demo-pass-123',
      confirmPassword: 'demo-pass-123',
    });
    request.flush({ name: 'Demo', username: 'demo1', role: 'ADMIN', passwordChanged: true, currentAcademicYear: null, idleTimeoutSeconds: 600 });

    expect(TestBed.inject(AuthStore).signedIn()).toBe(true);
    expect(navigate).toHaveBeenCalledWith('/');
  });

  it('shows "taken" under the username when the server refuses it', () => {
    create(true);
    fill({ name: 'Demo', username: 'demo1', password: 'demo-pass-123', confirmPassword: 'demo-pass-123' });
    submit();

    controller
      .expectOne(SIGNUP)
      .flush(
        { status: 422, fieldErrors: [{ field: 'username', message: 'This username is taken.' }] },
        { status: 422, statusText: 'Unprocessable Entity' },
      );
    fixture.detectChanges();

    expect(el().querySelector('[data-testid="error-username"]')?.textContent).toContain('This username is taken.');
    expect(navigate).not.toHaveBeenCalledWith('/');
  });
});
