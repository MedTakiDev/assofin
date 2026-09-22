import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';
import {provideHttpClient} from '@angular/common/http';
import {providePrimeNG} from 'primeng/config';
import Aura from '@primeuix/themes/aura';
import {APP_CONFIG_VALUE} from '@core/config/app.config';

export const appConfig: ApplicationConfig = {
  providers: [
    // 1. Core Engine
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),

    // 2. Data Network Layer
    provideHttpClient(),

    // 3. UI Presentation Layer
    providePrimeNG({
      theme: {
        preset: Aura,
        options: {
          darkModeSelector: false,
        },
      },
      // Enables modern built-in CSS transitions instead of legacy JS triggers
      license: APP_CONFIG_VALUE.primeNgKey,
    }),
  ],
};
