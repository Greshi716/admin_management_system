import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  input,
  OnDestroy,
  output,
  signal,
  viewChild,
} from '@angular/core';

/**
 * Choose, preview, replace and remove a photo (FR-005). It only picks the file; the screen sends it to the server,
 * which decides whether it is acceptable. The reason for a refusal is passed in as `error` and shown beside the
 * photo. `currentUrl` is the photo that is already saved (edit screen), shown until another file is chosen.
 */
@Component({
  selector: 'app-photo-upload',
  template: `
    <div class="flex items-center gap-4">
      @if (previewUrl() ?? currentUrl(); as shown) {
        <img [src]="shown" alt="Selected photo" class="h-20 w-20 rounded-full object-cover" data-testid="photo-preview" />
      } @else {
        <span class="inline-flex h-20 w-20 items-center justify-center rounded-full bg-page text-sm text-muted" aria-hidden="true">
          No photo
        </span>
      }
      <div class="flex flex-col gap-2">
        <div class="flex flex-wrap gap-2">
          <label class="btn btn-secondary btn-outlined btn-sm cursor-pointer" [class.pointer-events-none]="disabled()">
            {{ previewUrl() || currentUrl() ? 'Replace photo' : 'Choose photo' }}
            <input
              #file
              type="file"
              class="sr-only"
              accept="image/jpeg,image/png"
              aria-label="Photo file"
              [disabled]="disabled()"
              (change)="onChosen($event)"
            />
          </label>
          @if (previewUrl() || currentUrl()) {
            <button type="button" class="btn btn-danger btn-outlined btn-sm" [disabled]="disabled()" (click)="remove()">
              Remove photo
            </button>
          }
        </div>
        <p class="m-0 text-xs text-muted">JPEG or PNG, up to 5 MB.</p>
        @if (error(); as message) {
          <p class="m-0 text-sm text-error-text" role="alert" data-testid="photo-error">{{ message }}</p>
        }
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PhotoUploadComponent implements OnDestroy {
  /** The saved photo address, if any. */
  readonly currentUrl = input<string | null>(null);
  /** The reason the server refused the photo; shown beside it. */
  readonly error = input<string | null>(null);
  readonly disabled = input(false);

  /** A file was chosen (the screen sends it to the server). */
  readonly chosen = output<File>();
  /** The admin removed the photo (the chosen one, or the saved one). */
  readonly removed = output<void>();

  protected readonly previewUrl = signal<string | null>(null);
  private readonly fileInput = viewChild<ElementRef<HTMLInputElement>>('file');

  protected onChosen(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.item(0);
    if (!file) {
      return;
    }
    this.revoke();
    this.previewUrl.set(URL.createObjectURL(file));
    this.chosen.emit(file);
  }

  protected remove(): void {
    this.clear();
    this.removed.emit();
  }

  /** Forgets the chosen file, for example after "Save and add next" clears the form. */
  clear(): void {
    this.revoke();
    this.previewUrl.set(null);
    const input = this.fileInput()?.nativeElement;
    if (input) {
      input.value = '';
    }
  }

  ngOnDestroy(): void {
    this.revoke();
  }

  private revoke(): void {
    const url = this.previewUrl();
    if (url) {
      URL.revokeObjectURL(url);
    }
  }
}
