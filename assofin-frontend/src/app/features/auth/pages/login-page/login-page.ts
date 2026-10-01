import {Component, inject, signal} from '@angular/core';
import {LoginForm} from '../../components/login-form/login-form';
import {SignupForm} from '../../components/signup-form/signup-form';
import {AuthService} from '@auth/services/auth.service';
import {Router} from '@angular/router';
import {LoginRequestDto} from '@auth/dto/login-request.dto';
import {NotificationService} from '@core/services/notification.service';

@Component({
  imports: [
    LoginForm,
    SignupForm
  ],
  selector: 'app-login-page',
  styleUrl: './login-page.css',
  templateUrl: './login-page.html',
})
export class LoginPage {
  private readonly authService = inject(AuthService);
  private readonly notificationService = inject(NotificationService);
  private readonly router = inject(Router);

  // View state signals
  mode = signal<AuthMode>('login');
  isLoading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);


  showLogin(): void {
    this.errorMessage.set(null);
    this.mode.set('login');
  }

  showSignup(): void {
    this.errorMessage.set(null);
    this.mode.set('signup');
  }

  /**
   * Receives credentials from LoginForm child and executes auth request
   */
  onLogin(credentials: LoginRequestDto): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.authService.login(credentials).subscribe({
      next: () => {
        this.isLoading.set(false);
        //this.router.navigateByUrl('/dashboard');//navigate user to the main view
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(err.error?.message || 'Invalid email or password. Please try again.');
        this.notificationService.show(err.message);
      },
    });
  }
}

export type AuthMode = 'login' | 'signup';
