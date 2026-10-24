import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { ToastService } from './toast.service';

describe('ToastService (FR-039)', () => {
  let add: ReturnType<typeof vi.fn>;
  let toast: ToastService;

  beforeEach(() => {
    add = vi.fn();
    TestBed.configureTestingModule({ providers: [{ provide: MessageService, useValue: { add } }] });
    toast = TestBed.inject(ToastService);
  });

  it('adds a success message', () => {
    toast.success('Academic year saved.');
    expect(add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success', detail: 'Academic year saved.' }));
  });

  it('adds warning, info and error messages with their severity', () => {
    toast.warn('Check the dates.');
    toast.info('Nothing to export.');
    toast.error('Could not save.');
    expect(add.mock.calls.map((c) => c[0].severity)).toEqual(['warn', 'info', 'error']);
  });

  it('lets the caller choose the heading', () => {
    toast.error('Detail', 'Custom heading');
    expect(add).toHaveBeenCalledWith(expect.objectContaining({ summary: 'Custom heading' }));
  });
});
