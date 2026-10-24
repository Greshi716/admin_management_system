import { inject, Injectable } from '@angular/core';
import { MessageService } from 'primeng/api';

/** The one place that shows a toast, so every screen reports success and failure the same way (FR-039). */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly messages = inject(MessageService);

  success(detail: string, summary = 'Saved'): void {
    this.messages.add({ severity: 'success', summary, detail, life: 4000 });
  }

  info(detail: string, summary = 'Note'): void {
    this.messages.add({ severity: 'info', summary, detail, life: 5000 });
  }

  warn(detail: string, summary = 'Please check'): void {
    this.messages.add({ severity: 'warn', summary, detail, life: 7000 });
  }

  error(detail: string, summary = 'Something went wrong'): void {
    this.messages.add({ severity: 'error', summary, detail, life: 8000 });
  }
}
