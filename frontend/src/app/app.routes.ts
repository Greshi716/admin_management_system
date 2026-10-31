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
    // Every signed-in screen lives inside the shell (top bar with tabs).
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
        path: 'students',
        loadChildren: () => import('./features/students/students.routes').then((m) => m.STUDENTS_ROUTES),
      },
      {
        path: 'yearchange',
        loadChildren: () => import('./features/yearchange/yearchange.routes').then((m) => m.YEAR_CHANGE_ROUTES),
      },
      {
        path: 'finance',
        loadChildren: () => import('./features/finance/finance.routes').then((m) => m.FINANCE_ROUTES),
      },
      {
        path: 'setup',
        loadComponent: () => import('./features/setup/setup-layout.component').then((m) => m.SetupLayoutComponent),
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'academic-years' },
          {
            path: 'audit-log',
            loadChildren: () => import('./features/audit/audit.routes').then((m) => m.AUDIT_ROUTES),
          },
          {
            path: '',
            loadChildren: () => import('./features/fees/fees.routes').then((m) => m.FEES_ROUTES),
          },
          {
            path: '',
            loadChildren: () => import('./features/masterdata/masterdata.routes').then((m) => m.MASTER_DATA_ROUTES),
          },
        ],
      },
      // The old addresses keep working (bookmarks, links in older notes).
      { path: 'master-data', pathMatch: 'full', redirectTo: 'setup' },
      { path: 'master-data/:screen', redirectTo: 'setup/:screen' },
      { path: 'audit-log', pathMatch: 'full', redirectTo: 'setup/audit-log' },
    ],
  },
  { path: '**', redirectTo: '' },
];
