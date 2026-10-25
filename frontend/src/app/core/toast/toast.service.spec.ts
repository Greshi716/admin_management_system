import { TestBed } from '@angular/core/testing';
import { ToastService } from './toast.service';

describe('ToastService (FR-039)', () => {
  let toast: ToastService;

  beforeEach(() => {
    vi.useFakeTimers();
    toast = TestBed.inject(ToastService);
  });

  afterEach(() => vi.useRealTimers());

  it('adds a success message', () => {
    toast.success('Academic year saved.');
    expect(toast.messages()).toEqual([expect.objectContaining({ severity: 'success', detail: 'Academic year saved.' })]);
  });

  it('adds warning, info and error messages with their severity', () => {
    toast.warn('Check the dates.');
    toast.info('Nothing to export.');
    toast.error('Could not save.');
    expect(toast.messages().map((m) => m.severity)).toEqual(['warn', 'info', 'error']);
  });

  it('lets the caller choose the heading', () => {
    toast.error('Detail', 'Custom heading');
    expect(toast.messages()[0].summary).toBe('Custom heading');
  });

  it('removes a message after its time, or when dismissed', () => {
    toast.success('Saved once.');
    toast.error('Failed once.');

    vi.advanceTimersByTime(4000);
    expect(toast.messages().map((m) => m.severity)).toEqual(['error']);

    toast.dismiss(toast.messages()[0].id);
    expect(toast.messages()).toEqual([]);
  });
});
