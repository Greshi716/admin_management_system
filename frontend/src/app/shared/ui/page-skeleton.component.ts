import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Grey placeholder blocks shown while a screen, or the choices a form needs, are loading, so the admin never sees
 * a blank screen (FR-038). Lists have their own skeleton rows inside the table.
 */
@Component({
  selector: 'app-page-skeleton',
  template: `
    <div role="status" aria-live="polite" data-testid="page-skeleton">
      <span class="sr-only">Loading</span>
      <div class="animate-pulse space-y-3" aria-hidden="true">
        @if (title()) {
          <div class="h-8 w-1/3 rounded bg-border"></div>
        }
        @for (line of lines(); track $index) {
          <div class="h-4 rounded bg-border" [style.width.%]="$last ? 60 : 100"></div>
        }
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PageSkeletonComponent {
  /** Whether to draw a title block above the lines. */
  readonly title = input(true);
  /** One entry per line to draw. */
  readonly lines = input<number[]>([1, 2, 3, 4]);
}
