import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ButtonModule } from 'primeng/button';
import { providePrimeNG } from 'primeng/config';
import { CampusFlowPreset } from './campusflow-preset';

@Component({ imports: [ButtonModule], template: '<p-button label="Save" />' })
class Host {}

describe('CampusFlow PrimeNG preset (R2 spike)', () => {
  it('applies the accent colour to the page styles', async () => {
    TestBed.configureTestingModule({
      providers: [providePrimeNG({ theme: { preset: CampusFlowPreset, options: { darkModeSelector: false } } })],
    });
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    await fixture.whenStable();

    const css = Array.from(document.head.querySelectorAll('style'))
      .map((s) => s.textContent ?? '')
      .join('\n')
      .toLowerCase();
    expect(css).toContain('#6d4ae0');
    expect(css).toContain('#8e84ad');
    expect(css).toContain('10px');
    expect(fixture.nativeElement.querySelector('button')).not.toBeNull();
  });
});
