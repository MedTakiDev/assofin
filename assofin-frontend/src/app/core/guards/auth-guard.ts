import { CanMatchFn, Router, UrlTree } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from '@auth/services/auth.service';
import { map, take } from 'rxjs/operators'; // Ensure correct import path
import { UserProfileDto } from '@auth/dto/user-profile.dto';

export const authGuard: CanMatchFn = (route, segments) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return authService.checkSession().pipe(
    take(1),
    map((userProfile: UserProfileDto): boolean | UrlTree => {
      if (userProfile) {
        return true;
      }

      // Best practice: Return a UrlTree instead of triggering a manual side-effect navigation
      return router.createUrlTree(['/login']);
    })
  );
};
