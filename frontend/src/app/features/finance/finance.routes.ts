import { Routes } from '@angular/router';
import { authGuard } from '../../core/auth/auth.guard';

/**
 * Finance (F3). It sits inside the shell, whose `canActivateChild` is `currentYearGuard`, so while today falls in no
 * academic year it sends the admin to Academic years as every other screen does (F0 FR-013).
 */
export const FINANCE_ROUTES: Routes = [
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./finance-layout.component').then((m) => m.FinanceLayoutComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'fee-collection' },
      {
        path: 'fee-collection',
        title: 'Collect fee',
        loadComponent: () => import('./receipt-form/receipt-form.component').then((m) => m.ReceiptFormComponent),
      },
      {
        path: 'receipts',
        pathMatch: 'full',
        title: 'Receipts',
        loadComponent: () => import('./receipt-list/receipt-list.component').then((m) => m.ReceiptListComponent),
      },
      {
        path: 'receipts/:serialNo',
        title: 'Receipt',
        loadComponent: () => import('./receipt-view/receipt-view.component').then((m) => m.ReceiptViewComponent),
      },
      {
        path: 'receipts/:serialNo/print',
        title: 'Print receipt',
        loadComponent: () => import('./receipt-print/receipt-print.component').then((m) => m.ReceiptPrintComponent),
      },
    ],
  },
];
