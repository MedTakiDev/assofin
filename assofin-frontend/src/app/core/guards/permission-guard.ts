import {CanMatchFn, Route, Router, UrlSegment} from '@angular/router';
import {AuthService} from '@auth/services/auth.service';
import {inject} from '@angular/core';
import {AppPermission} from '@auth/dto/user-profile.dto';
/**
 * 🛡️ Enterprise Feature Read Permission Guard
 * @param feature The application domain module being requested (e.g. 'user', 'invoice')
 * Automatically appends ':read' to enforce macro structural route clearance.
 */
export const permissionGuard = (feature: string): CanMatchFn => {
  return (route:Route, segment:UrlSegment[])=>{
    const authService:AuthService = inject(AuthService);
    const router:Router = inject(Router);

    // 1. Defend against completely unauthenticated guests
    if (authService.isInitialized() && !authService.isAuthenticated()) {
      return router.createUrlTree(['/login']);
    }

    const requiredReadPermission = `${feature}:read` as AppPermission;
    if (authService.hasPermission(requiredReadPermission)) {
      return true; // Clearance verified! Safe to download and load module code.
    }
    return router.createUrlTree(['/dashboard']);
  }
};
