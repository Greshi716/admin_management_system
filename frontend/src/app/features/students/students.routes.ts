import { Routes } from '@angular/router';
import { authGuard } from '../../core/auth/auth.guard';

/** Student screens (F1). All of them need a signed-in admin. */
export const STUDENTS_ROUTES: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'Students',
    canActivate: [authGuard],
    loadComponent: () => import('./student-list/student-list.component').then((m) => m.StudentListComponent),
  },
  {
    path: 'new',
    title: 'Register student',
    canActivate: [authGuard],
    loadComponent: () => import('./student-form/student-form.component').then((m) => m.StudentFormComponent),
  },
  {
    path: ':id',
    title: 'Student',
    canActivate: [authGuard],
    loadComponent: () => import('./student-profile/student-profile.component').then((m) => m.StudentProfileComponent),
  },
];
