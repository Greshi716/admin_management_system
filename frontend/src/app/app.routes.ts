import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { currentYearGuard } from './core/auth/current-year.guard';

export const routes: Routes = [
  {
    path: 'sign-in',
    title: 'Sign in',
    loadComponent: () => import('./features/auth/sign-in/sign-in.component').then((m) => m.SignInComponent),
  },
  {
    // Every signed-in screen lives inside the shell (sidebar and top bar).
    path: '',
    canActivate: [authGuard],
    canActivateChild: [currentYearGuard],
    loadComponent: () => import('./core/layout/shell.component').then((m) => m.ShellComponent),
    children: [
      {
        path: '',
        pathMatch: 'full',
        title: 'CampusFlow',
        loadComponent: () => import('./features/home/home.component').then((m) => m.HomeComponent),
      },
      {
        path: 'audit-log',
        loadChildren: () => import('./features/audit/audit.routes').then((m) => m.AUDIT_ROUTES),
      },
      {
        path: 'students',
        loadChildren: () => import('./features/students/students.routes').then((m) => m.STUDENTS_ROUTES),
      },
      {
        path: 'master-data',
        loadChildren: () => import('./features/masterdata/masterdata.routes').then((m) => m.MASTER_DATA_ROUTES),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
