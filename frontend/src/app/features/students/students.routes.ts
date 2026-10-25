import { Routes } from '@angular/router';
import { authGuard } from '../../core/auth/auth.guard';

/** Student screens (F1). All of them need a signed-in admin. */
export const STUDENTS_ROUTES: Routes = [
  {
    path: 'new',
    title: 'Register student',
    canActivate: [authGuard],
    loadComponent: () => import('./student-form/student-form.component').then((m) => m.StudentFormComponent),
  },
];
