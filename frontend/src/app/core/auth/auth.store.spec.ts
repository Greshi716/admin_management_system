import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthStore } from './auth.store';
import { Me } from './me';

const me: Me = {
  name: 'Test Admin',
  username: 'testadmin',
  role: 'ADMIN',
  passwordChanged: false,
  currentAcademicYear: null,
  idleTimeoutSeconds: 600,
};

describe('AuthStore (FR-001, FR-006, FR-007, FR-009)', () => {
  let store: AuthStore;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    store = TestBed.inject(AuthStore);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  it('starts signed out', () => {
    expect(store.signedIn()).toBe(false);
    expect(store.passwordChanged()).toBe(true);
    expect(store.idleTimeoutSeconds()).toBe(600);
  });

  it('asks the server who is signed in once and reuses the answer', () => {
    const answers: boolean[] = [];

    store.ensureLoaded().subscribe((signedIn) => answers.push(signedIn));
    controller.expectOne('/api/auth/me').flush(me);
    store.ensureLoaded().subscribe((signedIn) => answers.push(signedIn));

    expect(answers).toEqual([true, true]);
    expect(store.user()?.username).toBe('testadmin');
    expect(store.passwordChanged()).toBe(false);
  });

  it('treats a 401 from the user check as signed out', () => {
    let result: boolean | undefined;

    store.ensureLoaded().subscribe((signedIn) => (result = signedIn));
    controller.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(result).toBe(false);
    expect(store.signedIn()).toBe(false);
  });

  it('keeps the user and clears the notice after a successful sign-in', () => {
    store.notice.set('You were signed out.');

    store.login('testadmin', 'secret-pass-1').subscribe();
    const request = controller.expectOne('/api/auth/login');
    expect(request.request.body).toEqual({ username: 'testadmin', password: 'secret-pass-1' });
    request.flush(me);

    expect(store.signedIn()).toBe(true);
    expect(store.notice()).toBeNull();
  });

  it('forgets the user on logout, even when the server call fails', () => {
    store.login('testadmin', 'x').subscribe();
    controller.expectOne('/api/auth/login').flush(me);

    store.logout().subscribe({ error: () => undefined });
    controller.expectOne('/api/auth/logout').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(store.signedIn()).toBe(false);
  });

  it('remembers that the password was changed', () => {
    store.login('testadmin', 'x').subscribe();
    controller.expectOne('/api/auth/login').flush(me);

    store.passwordWasChanged();

    expect(store.passwordChanged()).toBe(true);
  });
});
