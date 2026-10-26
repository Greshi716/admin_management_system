import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthStore } from '../../core/auth/auth.store';

/** The page after sign-in, inside the app shell: a welcome and shortcuts to the master data screens. */
@Component({
  selector: 'app-home',
  imports: [RouterLink],
  templateUrl: './home.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomeComponent {
  protected readonly store = inject(AuthStore);

  protected readonly cards = [
    {
      title: 'Academic years',
      help: 'April to March years; the one containing today is current.',
      link: '/setup/academic-years',
      icon: 'M8 2v4M16 2v4M3 10h18M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z',
    },
    {
      title: 'Standards',
      help: 'Classes in display order, with the stream where it applies.',
      link: '/setup/standards',
      icon: 'M12 2l10 5-10 5L2 7l10-5zM2 17l10 5 10-5M2 12l10 5 10-5',
    },
    {
      title: 'Divisions',
      help: 'Sections of a standard, for each academic year.',
      link: '/setup/divisions',
      icon: 'M3 3h7v7H3zM14 3h7v7h-7zM14 14h7v7h-7zM3 14h7v7H3z',
    },
    {
      title: 'Student categories',
      help: 'Codes such as RTE or STAFF and whether they affect fees.',
      link: '/setup/student-categories',
      icon: 'M20.6 13.4l-7.2 7.2a2 2 0 0 1-2.8 0L2 12V2h10l8.6 8.6a2 2 0 0 1 0 2.8zM7 7h.01',
    },
  ];
}
