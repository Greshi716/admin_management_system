import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, provideRouter, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { firstValueFrom, isObservable, Observable, of } from 'rxjs';
import { authGuard } from './auth.guard';
import { AuthStore } from './auth.store';

describe('authGuard (FR-007)', () => {
  function run(signedIn: boolean): Promise<boolean | UrlTree> {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthStore, useValue: { ensureLoaded: () => of(signedIn), signedIn: signal(signedIn) } }],
    });
    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );
    return firstValueFrom(isObservable(result) ? (result as Observable<boolean | UrlTree>) : of(result as boolean));
  }

  it('lets a signed-in admin through', async () => {
    expect(await run(true)).toBe(true);
  });

  it('sends anyone else to the sign-in page', async () => {
    const result = await run(false);

    expect(result).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/sign-in');
  });
});
