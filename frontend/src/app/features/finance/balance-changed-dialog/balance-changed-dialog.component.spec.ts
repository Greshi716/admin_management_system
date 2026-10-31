import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FeeCollection } from '../receipts.service';
import { BalanceChangedDialogComponent } from './balance-changed-dialog.component';

describe('BalanceChangedDialogComponent (FR-016)', () => {
  let fixture: ComponentFixture<BalanceChangedDialogComponent>;

  function open(latest: FeeCollection | null): void {
    fixture = TestBed.createComponent(BalanceChangedDialogComponent);
    fixture.componentRef.setInput('latest', latest);
    fixture.detectChanges();
  }

  const el = () => fixture.nativeElement as HTMLElement;

  it('says the balance has changed and offers only Refresh', () => {
    open({ balanceVersion: 'r2' } as FeeCollection);
    const refreshed = vi.fn<() => void>();
    fixture.componentInstance.refresh.subscribe(() => refreshed());

    expect(el().textContent).toContain('The balance has changed');
    expect(el().textContent).toContain('Nothing was saved');
    expect(el().querySelector('[aria-label="Close"]')).toBeNull();
    const buttons = Array.from(el().querySelectorAll('button'));
    expect(buttons.map((b) => b.textContent?.trim())).toEqual(['Refresh']);
    buttons[0].click();
    expect(refreshed).toHaveBeenCalledTimes(1);
  });

  it('shows nothing while the balance is unchanged', () => {
    open(null);

    expect(el().querySelector('[data-testid="balance-changed-text"]')).toBeNull();
  });
});
