import {
  ChangeDetectionStrategy,
  Component,
  effect,
  ElementRef,
  input,
  model,
  untracked,
  viewChild,
} from '@angular/core';

let nextId = 0;

/**
 * A modal dialog on the browser's own `<dialog>` element, which gives focus trapping, the Escape key and the
 * backdrop for free. The content is only created while the dialog is open, so a closed dialog adds nothing to
 * the page. `visible` is a two-way binding: it turns false when the admin closes the dialog.
 */
@Component({
  selector: 'app-dialog',
  template: `
    <dialog
      #dialog
      class="m-auto w-[calc(100vw-2rem)] rounded-card border border-border bg-surface p-0 text-ink shadow-2xl backdrop:bg-ink/40"
      [style.maxWidth]="width()"
      [attr.aria-labelledby]="titleId"
      (cancel)="onCancel($event)"
      (close)="visible.set(false)"
    >
      @if (visible()) {
        <div class="flex items-start justify-between gap-4 px-6 pt-5">
          <h2 class="m-0 text-xl font-bold" [id]="titleId">{{ header() }}</h2>
          @if (closable()) {
            <button type="button" class="link-button -mr-2 -mt-1 text-2xl leading-none text-muted" aria-label="Close" (click)="visible.set(false)">
              &times;
            </button>
          }
        </div>
        <div class="px-6 pb-6 pt-4">
          <ng-content />
        </div>
      }
    </dialog>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DialogComponent {
  readonly header = input.required<string>();
  readonly visible = model(false);
  /** False for a dialog that must be answered with one of its own buttons. */
  readonly closable = input(true);
  readonly width = input('26rem');

  protected readonly titleId = `app-dialog-title-${nextId++}`;
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  constructor() {
    effect(() => {
      const open = this.visible();
      const element = this.dialog().nativeElement;
      untracked(() => {
        if (open && !element.open) {
          // jsdom (unit tests) has no showModal; every real browser does.
          if (typeof element.showModal === 'function') {
            element.showModal();
          } else {
            element.setAttribute('open', '');
          }
        } else if (!open && element.open) {
          if (typeof element.close === 'function') {
            element.close();
          } else {
            element.removeAttribute('open');
          }
        }
      });
    });
  }

  /** Escape: close, unless the dialog has to be answered with its own buttons. */
  protected onCancel(event: Event): void {
    event.preventDefault();
    if (this.closable()) {
      this.visible.set(false);
    }
  }
}
