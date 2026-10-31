import { Routes } from '@angular/router';
import { authGuard } from '../../core/auth/auth.guard';

export const YEAR_CHANGE_ROUTES: Routes = [
  {
    path: 'promote/:standardId',
    title: 'Promote Students',
    canActivate: [authGuard],
    loadComponent: () => import('./promote/promote.component').then((m) => m.PromoteComponent),
  },
];
