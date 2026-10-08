import {Injectable, inject, signal, computed, Service} from '@angular/core';
import {Observable, tap, catchError, of, throwError} from 'rxjs';
import {AuthRepository, TempTokenResponse} from '../repositories/auth.repository';
import {AppPermission, AuthUserProfileDto} from '../dto/auth-user-profile.dto';
import {LoginRequestDto} from '../dto/login-request.dto';
import {RegisterRequestDto} from '../dto/register-request.dto';
import {AuthResponseDto} from '@auth/dto/auth-response.dto';


@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly authRepository = inject(AuthRepository);

  // Core state signals
  private readonly currentUserSignal = signal<AuthUserProfileDto | null>(null);
  private readonly isInitializedSignal = signal<boolean>(false);

  // Readonly computed signals for global application consumption
  readonly currentUser = this.currentUserSignal.asReadonly();
  readonly isAuthenticated = computed(() => !!this.currentUserSignal());
  readonly isInitialized = this.isInitializedSignal.asReadonly();

  // Add this method inside your existing Injectable AuthService class
  /**
   * Synchronously checks if the logged-in user possesses a required permission
   */
  hasPermission(requiredPermission: AppPermission): boolean {
    const user = this.currentUser();
    if (!user) {
      return false;
    }

    return user.role.permissions.includes(requiredPermission);
  }

  /**
   * Synchronously checks if the logged-in user possesses AT LEAST ONE of multiple permissions
   */
  hasAnyPermission(requiredPermissions: AppPermission[]): boolean {
    const user = this.currentUser();
    if (!user) {
      return false;
    }

    return requiredPermissions.some(permission => user.role.permissions.includes(permission));
  }

  /**
   * Called on application boot (e.g. via APP_INITIALIZER or AuthGuard)
   * to verify an active session cookie without throwing unhandled exceptions.
   */
  // ============================================================
  // Session
  // ============================================================

  checkSession(): Observable<AuthUserProfileDto | null> {
    /*
     * If the application already knows the authenticated user,
     * don't make another /auth/me request.
     */
    if (this.isInitialized()) {
      return of(this.currentUser());
    }
    /*
     * First authentication check.
     *
     * Backend:
     *
     * 200 → authenticated
     * 401 → unauthenticated
     */
    return this.authRepository.getSession().pipe(
      tap((user) => {
        this.currentUserSignal.set(user);
        this.isInitializedSignal.set(true);
      }),
      catchError((error) => {
        /*
         * Authentication failed.
         *
         * The backend AuthenticationEntryPoint has already
         * handled the invalid JWT and cleared the HttpOnly cookie.
         *
         * Angular only needs to update its local state.
         */
        this.clearSession();
        return throwError(() => error);
      }),
    );
  }

  /**
   * Imperative Login method. Emits response Observable to component subscribers.
   */
  // ============================================================
  // Authentication
  // ============================================================
  login(credentials: LoginRequestDto): Observable<AuthResponseDto> {
    return this.authRepository
      .login(credentials)
  }

  /**
   * Imperative Signup method. Emits response Observable to component subscribers.
   */
  signup(payload: RegisterRequestDto): Observable<AuthUserProfileDto> {
    return this.authRepository
      .signup(payload)
      .pipe(
        tap((user) => this.currentUserSignal.set(user))
      );
  }

  // ============================================================
  // Explicit logout
  // ============================================================

  /**
   * Explicit user logout.
   *
   * This is different from clearSession().
   *
   * logout() → tells backend to invalidate/delete cookie
   * clearSession() → only clears Angular state
   */
  logout(): Observable<void> {
    return this.authRepository.logout().pipe(tap(() => this.currentUserSignal.set(null)));
  }

  // ============================================================
  // Local authentication state
  // ============================================================

  /**
   * Clears only the Angular authentication state.
   *
   * This does NOT make an HTTP request.
   *
   * Used when the backend tells us that the current
   * authentication is no longer valid.
   */
  clearSession(): void {

    this.currentUserSignal.set(null);
    this.isInitializedSignal.set(true);
  }
}
