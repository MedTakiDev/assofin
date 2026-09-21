import { InjectionToken } from '@angular/core';

export interface AppConfig {
  apiUrl: string;
  timeout: number;
  primeNgKey: string;
}


export const AppConfigValue: AppConfig = {
  // Points to your local dev network address or domain proxy
  apiUrl: import.meta.env.NG_APP_API_BASE_URL,
  timeout: 10000,
  primeNgKey:import.meta.env.NG_APP_PRIMEUI_LICENSE_KEY,
};
