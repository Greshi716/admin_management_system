import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** The letters shown when a student has no photo: first letter of the surname and of the first name. */
export function initialsOf(surname: string | null | undefined, firstName: string | null | undefined): string {
  const first = (text: string | null | undefined) => (text ?? '').trim().charAt(0).toUpperCase();
  return `${first(surname)}${first(firstName)}`;
}

/**
 * A student's photo, or their initials when there is none (FR-005). The photo address carries the photo version, so
 * the browser keeps the picture for a year and loads a new one only after it changed.
 */
@Component({
  selector: 'app-avatar',
  template: `
    @if (photoUrl(); as url) {
      <img
        [src]="url"
        [alt]="'Photo of ' + label()"
        [class]="'rounded-full object-cover ' + sizeClass()"
        data-testid="avatar-photo"
      />
    } @else {
      <span
        [class]="'inline-flex items-center justify-center rounded-full bg-accent-soft font-bold text-accent-text ' + sizeClass()"
        [attr.aria-label]="'Initials of ' + label()"
        role="img"
        data-testid="avatar-initials"
        >{{ initials() }}</span
      >
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AvatarComponent {
  readonly studentId = input<number | null>(null);
  readonly photoVersion = input<number | null | undefined>(null);
  readonly surname = input<string | null>('');
  readonly firstName = input<string | null>('');
  /** `list` is the small round picture in the list; `profile` is the large one in the profile heading. */
  readonly size = input<'list' | 'profile'>('list');

  protected readonly initials = computed(() => initialsOf(this.surname(), this.firstName()));
  protected readonly label = computed(() => `${this.firstName() ?? ''} ${this.surname() ?? ''}`.trim());
  protected readonly sizeClass = computed(() =>
    this.size() === 'profile' ? 'h-20 w-20 text-2xl' : 'h-10 w-10 text-sm',
  );
  protected readonly photoUrl = computed(() => {
    const id = this.studentId();
    const version = this.photoVersion();
    return id !== null && version !== null && version !== undefined ? `/api/students/${id}/photo?v=${version}` : null;
  });
}
