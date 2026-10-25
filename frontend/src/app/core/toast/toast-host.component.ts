import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ToastService } from './toast.service';

/** Draws the messages of `ToastService` in the top-right corner. Put it once in the root component. */
@Component({
  selector: 'app-toast',
  template: `
    <div class="pointer-events-none fixed right-4 top-4 z-[60] flex w-[min(24rem,calc(100vw-2rem))] flex-col gap-3">
      @for (message of toast.messages(); track message.id) {
        <div
          class="pointer-events-auto flex items-start gap-3 rounded-card border bg-surface p-4 shadow-lg"
          [class.border-success/40]="message.severity === 'success'"
          [class.border-border]="message.severity === 'info'"
          [class.border-warning-text/40]="message.severity === 'warn'"
          [class.border-danger/40]="message.severity === 'error'"
          [attr.role]="message.severity === 'error' ? 'alert' : 'status'"
        >
          <span
            class="mt-1 h-2.5 w-2.5 shrink-0 rounded-full"
            [class.bg-success]="message.severity === 'success'"
            [class.bg-accent]="message.severity === 'info'"
            [class.bg-warning-text]="message.severity === 'warn'"
            [class.bg-danger]="message.severity === 'error'"
            aria-hidden="true"
          ></span>
          <div class="min-w-0 flex-1">
            <p class="m-0 text-sm font-bold">{{ message.summary }}</p>
            <p class="m-0 mt-0.5 text-sm text-muted">{{ message.detail }}</p>
          </div>
          <button type="button" class="link-button text-lg leading-none text-muted" aria-label="Dismiss" (click)="toast.dismiss(message.id)">
            &times;
          </button>
        </div>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ToastHostComponent {
  protected readonly toast = inject(ToastService);
}
