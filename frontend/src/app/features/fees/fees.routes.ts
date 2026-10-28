import { Routes } from '@angular/router';
import { authGuard } from '../../core/auth/auth.guard';

/**
 * The fee structure screen (F2 FR-001). It sits inside the shell, whose `canActivateChild` is `currentYearGuard`,
 * so while today falls in no academic year it sends the admin to Academic years as every other screen does (FR-013).
 */
export const FEES_ROUTES: Routes = [
  {
    path: 'fees',
    title: 'Fee structure',
    canActivate: [authGuard],
    loadComponent: () => import('./fee-structure.component').then((m) => m.FeeStructureComponent),
  },
];
