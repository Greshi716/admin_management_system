import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PhotoUploadComponent } from './photo-upload.component';

describe('PhotoUploadComponent (FR-005)', () => {
  let fixture: ComponentFixture<PhotoUploadComponent>;
  let element: HTMLElement;

  beforeEach(() => {
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:preview');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    fixture = TestBed.createComponent(PhotoUploadComponent);
    element = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  afterEach(() => vi.restoreAllMocks());

  function choose(file: File): void {
    const input = element.querySelector('input[type="file"]') as HTMLInputElement;
    Object.defineProperty(input, 'files', { value: { item: () => file, length: 1 }, configurable: true });
    input.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  it('emits the chosen file and shows a preview', () => {
    const chosen: File[] = [];
    fixture.componentInstance.chosen.subscribe((file) => chosen.push(file));
    const file = new File(['x'], 'photo.png', { type: 'image/png' });

    choose(file);

    expect(chosen).toEqual([file]);
    expect(element.querySelector('[data-testid="photo-preview"]')?.getAttribute('src')).toBe('blob:preview');
    expect(element.textContent).toContain('Replace photo');
  });

  it('emits removed and goes back to "Choose photo"', () => {
    let removed = 0;
    fixture.componentInstance.removed.subscribe(() => removed++);
    choose(new File(['x'], 'photo.png', { type: 'image/png' }));

    const remove = Array.from(element.querySelectorAll('button')).find((b) => b.textContent?.includes('Remove'));
    (remove as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(removed).toBe(1);
    expect(element.querySelector('[data-testid="photo-preview"]')).toBeNull();
    expect(element.textContent).toContain('Choose photo');
  });

  it('shows the saved photo and the refusal beside the photo', () => {
    fixture.componentRef.setInput('currentUrl', '/api/students/3/photo?v=9');
    fixture.componentRef.setInput('error', 'The file must be a JPEG or PNG photo.');
    fixture.detectChanges();

    expect(element.querySelector('[data-testid="photo-preview"]')?.getAttribute('src')).toBe(
      '/api/students/3/photo?v=9',
    );
    expect(element.querySelector('[data-testid="photo-error"]')?.textContent).toContain('JPEG or PNG');
  });

  it('only offers JPEG and PNG files', () => {
    expect(element.querySelector('input[type="file"]')?.getAttribute('accept')).toBe('image/jpeg,image/png');
  });
});
