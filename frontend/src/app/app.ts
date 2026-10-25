import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import {
  NavigationCancel,
  NavigationCancellationCode,
  NavigationEnd,
  NavigationError,
  Router,
  RouterOutlet,
} from '@angular/router';
import { filter, take } from 'rxjs';
import { ToastHostComponent } from './core/toast/toast-host.component';
import { PageSkeletonComponent } from './shared/ui/page-skeleton.component';

@Component({
  imports: [RouterOutlet, ToastHostComponent, PageSkeletonComponent],
  selector: 'app-root',
  templateUrl: './app.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  /** True until the first screen is ready, so the admin sees a placeholder and never a blank page (FR-038). */
  protected readonly starting = signal(true);

  constructor() {
    inject(Router)
      .events.pipe(
        // a redirect cancels one navigation and starts the next, so it does not count as the first screen being ready
        filter(
          (event) =>
            event instanceof NavigationEnd ||
            event instanceof NavigationError ||
            (event instanceof NavigationCancel && event.code !== NavigationCancellationCode.Redirect),
        ),
        take(1),
      )
      .subscribe(() => this.starting.set(false));
  }
}
