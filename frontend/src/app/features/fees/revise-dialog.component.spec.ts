import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReviseDialogComponent } from './revise-dialog.component';

describe('ReviseDialogComponent (FR-004)', () => {
  let fixture: ComponentFixture<ReviseDialogComponent>;
  let confirmed: ReturnType<typeof vi.fn<() => void>>;
  let cancelled: ReturnType<typeof vi.fn<() => void>>;

  function open(studentCount: number | null): void {
    fixture = TestBed.createComponent(ReviseDialogComponent);
    fixture.componentRef.setInput('studentCount', studentCount);
    confirmed = vi.fn<() => void>();
    cancelled = vi.fn<() => void>();
    fixture.componentInstance.confirmed.subscribe(() => confirmed());
    fixture.componentInstance.cancelled.subscribe(() => cancelled());
    fixture.detectChanges();
  }

  const element = () => fixture.nativeElement as HTMLElement;
  const button = (label: string) =>
    Array.from(element().querySelectorAll('button')).find((b) => b.textContent?.includes(label));

  it('says every fee of the year will be recalculated and how many students it touches', () => {
    open(5);

    expect(element().querySelector('[data-testid="revise-warning"]')?.textContent).toContain(
      "Every student's fee for the year will be recalculated.",
    );
    expect(element().querySelector('[data-testid="revise-count"]')?.textContent).toContain('5 students');
  });

  it('uses the singular for one student', () => {
    open(1);

    expect(element().querySelector('[data-testid="revise-count"]')?.textContent).toContain('1 student ');
  });

  it('shows nothing while there is no warning to answer', () => {
    open(null);

    expect(element().querySelector('[data-testid="revise-warning"]')).toBeNull();
  });

  it('Confirm and Cancel each say so once', () => {
    open(5);

    button('Confirm')?.click();
    expect(confirmed).toHaveBeenCalledTimes(1);
    expect(cancelled).not.toHaveBeenCalled();

    button('Cancel')?.click();
    expect(cancelled).toHaveBeenCalledTimes(1);
  });
});
