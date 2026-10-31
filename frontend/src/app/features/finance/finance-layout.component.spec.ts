import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { FinanceLayoutComponent } from './finance-layout.component';
import { ShellComponent } from '../../core/layout/shell.component';

describe('Finance tab (FR-014)', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideRouter([])] }));

  it('shows one pill, Fee collection, and none of the modules that are not built', () => {
    const fixture = TestBed.createComponent(FinanceLayoutComponent);
    fixture.detectChanges();
    const links = Array.from<HTMLAnchorElement>(fixture.nativeElement.querySelectorAll('nav[aria-label="Finance"] a'));
    expect(links.map((a) => a.textContent?.trim())).toEqual(['Fee collection']);
    const text = fixture.nativeElement.textContent as string;
    for (const hidden of ['Fee tracker', 'Payroll', 'Expenses and fund', 'Dashboard', 'Approvals', 'Staff', 'Reports']) {
      expect(text).not.toContain(hidden);
    }
  });

  it('shows the two views of Fee collection, Collect fee and Receipts', () => {
    const fixture = TestBed.createComponent(FinanceLayoutComponent);
    fixture.detectChanges();
    const links = Array.from<HTMLAnchorElement>(fixture.nativeElement.querySelectorAll('nav[aria-label="Fee collection views"] a'));
    expect(links.map((a) => a.textContent?.trim())).toEqual(['Collect fee', 'Receipts']);
    expect(links.map((a) => a.getAttribute('href'))).toEqual(['/finance/fee-collection', '/finance/receipts']);
  });

  it('adds the Finance tab to the top bar between Students and Setup', () => {
    const shell = TestBed.createComponent(ShellComponent).componentInstance as unknown as { tabs: { label: string; link: string }[] };
    expect(shell.tabs.map((t) => t.label)).toEqual(['Students', 'Finance', 'Setup']);
    expect(shell.tabs[1].link).toBe('/finance');
  });
});
