import { Routes } from '@angular/router';
import {authGuard} from '@core/guards/auth-guard';

export const routes: Routes = [
  // ───────── PUBLIC ROUTES ─────────
  {
    path: 'auth',
    loadComponent: () =>
      import('./layouts/auth-layout/auth-layout')
        .then(m => m.AuthLayout),

    loadChildren: () =>
      import('./features/auth/auth.routes')
        .then(m => m.AUTH_ROUTES)
  },

  // ───────── PROTECTED ROUTES ─────────
  {
    path: '',
    //canMatch: [authGuard], activate this in case we set the authentication
    canMatch:[authGuard],
    loadComponent: () =>
      import('./layouts/main-layout/main-layout')
        .then(m => m.MainLayout),

    /* append all children here and use
    children: [

      {
        path: 'users',
        loadChildren: () =>
          import('./features/users/users.routes')
            .then(m => m.USERS_ROUTES)
      }// other routes of domains should be added here and the route file added should export domain navigation

    ]*/
  },

  // ───────── FALLBACK ─────────
  {
    path: '**',
    redirectTo: ''
  }

];
