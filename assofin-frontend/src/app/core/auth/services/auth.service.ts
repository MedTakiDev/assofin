import {Injectable, inject, signal, computed, Service} from '@angular/core';
import { Observable, tap, catchError, of } from 'rxjs';
import {AuthRepository} from '../repositories/auth.repository';
import {UserProfileDto} from '../dto/user-profile.dto';
import {LoginRequestDto} from '../dto/login-request.dto';
import {RegisterRequestDto} from '../dto/register-request.dto';


@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly authRepository = inject(AuthRepository);

  // Core state signals
  private readonly currentUserSignal = signal<UserProfileDto | null>(null);
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
  checkSession(): Observable<UserProfileDto | null> {
    return this.authRepository.getSession().pipe(
      tap((user) => {
        this.currentUserSignal.set(user);
        this.isInitializedSignal.set(true);
      }),
      catchError(() => {
        this.currentUserSignal.set(null);
        this.isInitializedSignal.set(true);
        return of(null);
      }),
    );
  }

  /**
   * Imperative Login method. Emits response Observable to component subscribers.
   */
  login(credentials: LoginRequestDto): Observable<UserProfileDto> {
    return this.authRepository
      .login(credentials)
      .pipe(tap((user) => this.currentUserSignal.set(user)));
  }

  /**
   * Imperative Signup method. Emits response Observable to component subscribers.
   */
  signup(payload: RegisterRequestDto): Observable<UserProfileDto> {
    return this.authRepository
      .signup(payload)
      .pipe(tap((user) => this.currentUserSignal.set(user)));
  }

  /**
   * Imperative Logout method. Clears client session signal upon success.
   */
  logout(): Observable<void> {
    return this.authRepository.logout().pipe(tap(() => this.currentUserSignal.set(null)));
  }
}
