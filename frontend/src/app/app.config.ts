import { provideHttpClient, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withViewTransitions } from '@angular/router';
import { MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { routes } from './app.routes';
import { errorInterceptor } from './core/http/error.interceptor';
import { CampusFlowPreset } from './theme/campusflow-preset';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withViewTransitions()),
    // The backend sets the XSRF-TOKEN cookie; Angular copies it into the X-XSRF-TOKEN header on writes.
    provideHttpClient(
      withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
      withInterceptors([errorInterceptor]),
    ),
    // PrimeNG 22 animates with CSS, so there is no animations provider to add.
    providePrimeNG({
      theme: {
        preset: CampusFlowPreset,
        options: { darkModeSelector: false },
      },
    }),
    MessageService,
  ],
};
