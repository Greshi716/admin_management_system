import { Injectable, signal } from '@angular/core';

export type ToastSeverity = 'success' | 'info' | 'warn' | 'error';

export interface ToastMessage {
  id: number;
  severity: ToastSeverity;
  summary: string;
  detail: string;
}

/** The one place that shows a toast, so every screen reports success and failure the same way (FR-039). */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly list = signal<ToastMessage[]>([]);
  private nextId = 1;

  /** The messages on screen, oldest first; shown by `ToastHostComponent`. */
  readonly messages = this.list.asReadonly();

  success(detail: string, summary = 'Saved'): void {
    this.show('success', summary, detail, 4000);
  }

  info(detail: string, summary = 'Note'): void {
    this.show('info', summary, detail, 5000);
  }

  warn(detail: string, summary = 'Please check'): void {
    this.show('warn', summary, detail, 7000);
  }

  error(detail: string, summary = 'Something went wrong'): void {
    this.show('error', summary, detail, 8000);
  }

  dismiss(id: number): void {
    this.list.update((messages) => messages.filter((message) => message.id !== id));
  }

  private show(severity: ToastSeverity, summary: string, detail: string, lifeMs: number): void {
    const id = this.nextId++;
    this.list.update((messages) => [...messages, { id, severity, summary, detail }]);
    setTimeout(() => this.dismiss(id), lifeMs);
  }
}
