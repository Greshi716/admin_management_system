import { Routes } from '@angular/router';
import { authGuard } from '../../core/auth/auth.guard';

/** The audit log screen (FR-026). It needs a signed-in admin (FR-007). */
export const AUDIT_ROUTES: Routes = [
  {
    path: '',
    title: 'Audit log',
    canActivate: [authGuard],
    loadComponent: () => import('./audit-log.component').then((m) => m.AuditLogComponent),
  },
];
