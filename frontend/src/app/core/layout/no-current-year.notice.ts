import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ACADEMIC_YEARS_ROUTE } from '../auth/current-year.guard';

/**
 * The amber panel shown on every screen while today falls in no academic year (FR-013, FR-028). It disappears as
 * soon as a year covering today is saved.
 */
@Component({
  selector: 'app-no-current-year-notice',
  imports: [RouterLink],
  template: `
    <div
      class="mb-6 rounded-card border border-warning-text/30 bg-warning-bg p-5 text-warning-text"
      role="alert"
      data-testid="no-current-year"
    >
      <p class="m-0 text-lg font-bold">No academic year covers today. Create one to continue.</p>
      <p class="mb-0 mt-1">
        Every other screen stays closed until an academic year that includes today's date exists.
        <a [routerLink]="academicYears" class="font-semibold text-warning-text underline">Go to Academic years</a>
      </p>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NoCurrentYearNoticeComponent {
  protected readonly academicYears = ACADEMIC_YEARS_ROUTE;
}
