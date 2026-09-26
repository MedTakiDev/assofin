import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '@auth/services/auth.service';
import {messageSeverity, NotificationService} from '@core/services/notification.service';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const notificationService = inject(NotificationService);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse) {

        switch (error.status) {
          // 🔒 1. Session Expirations (Expired Cookie)
          case 401:
            //'Session expired. Please log in again.', 'error'
            notificationService.show({severity: messageSeverity.error, summary:'Session expired. Please log in again.'});
            authService.logout().subscribe({
              complete: () => router.navigateByUrl('/login')
            });
            break;

          // 🚫 2. Tampered Security/Unauthorized Page Bounds
          case 403:
            notificationService.show({severity:messageSeverity.error, summary:'Access Denied: Insufficient authorization permissions.'});
            authService.logout().subscribe({
              complete: () => router.navigateByUrl('/login')
            });
            break;

          // 💥 3. Infrastructure Global Server Crashes
          case 500:
          case 503:
            notificationService.show({severity:messageSeverity.error, summary:'Internal service failure. Our engineers are investigating.'});
            break;

          // 📡 4. Network Loss / Severed Cable Connections
          case 0:
            notificationService.show({severity:messageSeverity.warning, summary:'Network error detected. Check your internet connection.'});
            break;

          // 🔄 5. Contextual Fallback (400 Bad Request, 422 Validation Error)
          default:
            // Do NOT show a global toast for form field errors.
            // Pass it straight down the line to the component's subscription observer.
            return throwError(() => error);
        }
      }

      // Stop the error from leaking out to component observers for codes handled above (401, 403, 500)
      return throwError(() => error);
    })
  );
};
