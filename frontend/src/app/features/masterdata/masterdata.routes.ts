import { Routes } from '@angular/router';
import { authGuard } from '../../core/auth/auth.guard';

/** Master data screens (FR-010 to FR-021). All of them need a signed-in admin (FR-007). */
export const MASTER_DATA_ROUTES: Routes = [
  {
    path: 'academic-years',
    title: 'Academic years',
    canActivate: [authGuard],
    loadComponent: () => import('./academic-years/academic-years.component').then((m) => m.AcademicYearsComponent),
  },
  {
    path: 'standards',
    title: 'Standards',
    canActivate: [authGuard],
    loadComponent: () => import('./standards/standards.component').then((m) => m.StandardsComponent),
  },
  {
    path: 'divisions',
    title: 'Divisions',
    canActivate: [authGuard],
    loadComponent: () => import('./divisions/divisions.component').then((m) => m.DivisionsComponent),
  },
  {
    path: 'student-categories',
    title: 'Student categories',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./categories/student-categories.component').then((m) => m.StudentCategoriesComponent),
  },
];
