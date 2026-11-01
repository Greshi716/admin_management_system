import { Routes } from '@angular/router';

export const ADMISSIONS_ROUTES: Routes = [
  {
    path: '',
    title: 'Admissions',
    loadComponent: () =>
      import('./list/admissions-list.component').then((m) => m.AdmissionsListComponent),
  },
  {
    path: ':id',
    title: 'Review Admission',
    loadComponent: () =>
      import('./review/admission-review.component').then((m) => m.AdmissionReviewComponent),
  },
];
