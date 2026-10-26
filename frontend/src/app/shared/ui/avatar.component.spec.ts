import { TestBed } from '@angular/core/testing';
import { AvatarComponent, initialsOf } from './avatar.component';

describe('initialsOf (FR-005)', () => {
  it('takes the first letter of the surname and of the first name, in capitals', () => {
    expect(initialsOf('testsurname', 'alpha')).toBe('TA');
  });

  it('copes with empty and missing names', () => {
    expect(initialsOf('', 'Alpha')).toBe('A');
    expect(initialsOf(null, undefined)).toBe('');
    expect(initialsOf('  Test ', ' Alpha')).toBe('TA');
  });
});

describe('AvatarComponent (FR-005)', () => {
  function render(inputs: Record<string, unknown>) {
    const fixture = TestBed.createComponent(AvatarComponent);
    for (const [name, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(name, value);
    }
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows the initials when there is no photo', () => {
    const element = render({ studentId: 5, photoVersion: null, surname: 'Testsurname', firstName: 'Alpha' });

    expect(element.querySelector('[data-testid="avatar-initials"]')?.textContent?.trim()).toBe('TA');
    expect(element.querySelector('img')).toBeNull();
  });

  it('shows the photo, addressed by its version, when there is one', () => {
    const element = render({ studentId: 5, photoVersion: 123, surname: 'Testsurname', firstName: 'Alpha' });

    expect(element.querySelector('img')?.getAttribute('src')).toBe('/api/students/5/photo?v=123');
    expect(element.querySelector('[data-testid="avatar-initials"]')).toBeNull();
  });

  it('uses a bigger picture in the profile', () => {
    const element = render({ studentId: 5, photoVersion: null, surname: 'A', firstName: 'B', size: 'profile' });

    expect(element.querySelector('[data-testid="avatar-initials"]')?.className).toContain('h-20');
  });
});
