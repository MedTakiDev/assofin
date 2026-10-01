import {Component, inject, signal} from '@angular/core';
import {LoginForm} from '../../components/login-form/login-form';
import {SignupForm} from '../../components/signup-form/signup-form';
import {AuthService} from '@auth/services/auth.service';
import {Router} from '@angular/router';

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

}

export type AuthMode = 'login' | 'signup';
