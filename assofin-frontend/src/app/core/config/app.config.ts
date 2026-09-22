import { InjectionToken } from '@angular/core';

export interface AppConfig {
  apiUrl: string;
  timeout: number;
  primeNgKey: string;
}

export const APP_CONFIG = new InjectionToken<AppConfig>('app.config');

export const APP_CONFIG_VALUE: AppConfig = {
  apiUrl: import.meta.env.NG_APP_API_BASE_URL,
  timeout: Number(import.meta.env.NG_APP_API_TIMEOUT) || 30_000,
  primeNgKey: import.meta.env.NG_APP_PRIMEUI_LICENSE_KEY,
};
