import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  {
    path: 'sign-in',
    title: 'Sign in',
    loadComponent: () => import('./features/auth/sign-in/sign-in.component').then((m) => m.SignInComponent),
  },
  {
    path: '',
    pathMatch: 'full',
    title: 'CampusFlow',
    canActivate: [authGuard],
    loadComponent: () => import('./features/home/home.component').then((m) => m.HomeComponent),
  },
  {
    path: 'master-data',
    loadChildren: () => import('./features/masterdata/masterdata.routes').then((m) => m.MASTER_DATA_ROUTES),
  },
  { path: '**', redirectTo: '' },
];
