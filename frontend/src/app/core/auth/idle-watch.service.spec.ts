import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { AuthStore } from './auth.store';
import { IdleWatchService, SESSION_ENDED_MESSAGE } from './idle-watch.service';

describe('IdleWatchService (FR-008)', () => {
  let router: { url: string; navigateByUrl: ReturnType<typeof vi.fn> };
  let store: AuthStore;
  let service: IdleWatchService;

  beforeEach(() => {
    router = { url: '/', navigateByUrl: vi.fn().mockResolvedValue(true) };
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: Router, useValue: router }],
    });
    store = TestBed.inject(AuthStore);
    service = TestBed.inject(IdleWatchService);
  });

  it('signs out locally, leaves a message and goes to the sign-in page', () => {
    service.sessionEnded();

    expect(store.signedIn()).toBe(false);
    expect(store.notice()).toBe(SESSION_ENDED_MESSAGE);
    expect(router.navigateByUrl).toHaveBeenCalledWith('/sign-in');
  });

  it('does not navigate again when already on the sign-in page', () => {
    router.url = '/sign-in';

    service.sessionEnded();

    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('makes no request of its own, so it cannot extend the session', () => {
    const controller = TestBed.inject(HttpTestingController);

    service.sessionEnded();

    controller.verify();
  });
});
