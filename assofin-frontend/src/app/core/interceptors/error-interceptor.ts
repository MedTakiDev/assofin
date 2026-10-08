import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '@auth/services/auth.service';
import {messageSeverity, NotificationService} from '@core/services/notification.service';

export const errorInterceptor: HttpInterceptorFn = (req,next,) => {

  const authService = inject(AuthService);
  const notificationService = inject(NotificationService);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse)) {
        return throwError(() => error);
      }
      switch (error.status) {
        // ======================================================
        // 400 Bad Request
        // ======================================================
        case 400:
          notificationService.show({
            severity: messageSeverity.error,
            summary: 'Invalid request.',
          });
          break;

        // ======================================================
        // 401 Unauthorized
        // ======================================================

        case 401:

          /*
           * IMPORTANT:
           *
           * Do NOT:
           *
           * - navigate
           * - call /auth/logout
           * - reload the application
           *
           * The interceptor only synchronizes the global
           * authentication state.
           *
           * The AuthGuard is responsible for navigation.
           */
          authService.clearSession();
          break;
        // ======================================================
        // 403 Forbidden
        // ======================================================
        case 403:
          notificationService.show({
            severity: messageSeverity.error,
            summary:
              'Access Denied: Insufficient authorization permissions.',
          });
          break;

        // ======================================================
        // 500 / 503
        // ======================================================
        case 500:
        case 503:
          notificationService.show({
            severity: messageSeverity.error,
            summary:
              'Internal service failure. Our engineers are investigating.',
          });
          break;

        // ======================================================
        // Network error
        // ======================================================
        case 0:
          notificationService.show({
            severity: messageSeverity.warning,
            summary:
              'Network error detected. Check your internet connection.',
          });
          break;

        // ======================================================
        // Other errors
        // ======================================================
        default:
          break;
      }

      /*
       * NEVER swallow the error.
       *
       * The component/service/repository that initiated
       * the request should still be able to handle it.
       */
      return throwError(() => error);
    }),
  );
};
