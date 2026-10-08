import { CanActivateFn } from '@angular/router';

import { CanMatchFn, Router, UrlTree } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from '@auth/services/auth.service';
import { map, take } from 'rxjs/operators';
import {catchError, of} from 'rxjs'; // Ensure correct import path
export const reverseAuthGuard: CanActivateFn = (route, state) => {

  const authService = inject(AuthService);
  const router = inject(Router);

  return authService.checkSession().pipe(

    take(1),
    map((user) => {
      /*
       * Authenticated.
       */
      if (!user) {
        return true;
      }
      /*
       * No authenticated user.
       */
      return router.createUrlTree([
        '/dashboard',
      ]);
    }),
    catchError(() => {
      /*
       * /auth/me returned 401.
       *
       * AuthService has already cleared the
       * application authentication state.
       *
       * This is the ONLY place responsible for
       * authentication-driven navigation.
       */
      return of(router.createUrlTree(['/dashboard',]),);
    }),
  );
};
