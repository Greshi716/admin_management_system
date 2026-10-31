import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { filter } from 'rxjs';
import { YearChangeService } from '../../features/yearchange/yearchange.service';

@Component({
  selector: 'app-promote-warning',
  imports: [CommonModule, RouterLink],
  template: `
    @if (status()?.active && (status()?.standards?.length ?? 0) > 0) {
      <div
        class="mb-6 rounded-card border border-warning-text/30 bg-warning-bg p-4 text-warning-text"
        role="alert"
        data-testid="promote-warning-bar"
      >
        <div class="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p class="m-0 font-bold">
              Promotions to the new academic year ({{ status()?.newYear }}) are not complete.
            </p>
            <p class="mb-0 mt-1 text-sm" data-testid="pending-standards">
              Standards still to promote:
              @for (std of status()?.standards; track std.standardId; let last = $last) {
                <span class="font-semibold">{{ std.name }}</span> ({{ std.pending }} pending){{ last ? '' : ', ' }}
              }
            </p>
          </div>
          @if (status()?.standards?.[0]; as first) {
            <a
              [routerLink]="['/yearchange/promote', first.standardId]"
              class="btn btn-secondary min-h-[44px] shrink-0 text-sm font-semibold"
              data-testid="promote-warning-link"
            >
              Promote {{ first.name }}
            </a>
          }
        </div>
      </div>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PromoteWarningComponent implements OnInit {
  private readonly yearChangeService = inject(YearChangeService);
  private readonly router = inject(Router);

  protected readonly status = this.yearChangeService.status;

  ngOnInit(): void {
    this.refresh();
    this.router.events
      .pipe(filter((event) => event instanceof NavigationEnd))
      .subscribe(() => {
        this.refresh();
      });
  }

  refresh(): void {
    this.yearChangeService.refreshStatus().subscribe({
      error: () => {
        // Silently ignore if not signed in or network error
      },
    });
  }
}
