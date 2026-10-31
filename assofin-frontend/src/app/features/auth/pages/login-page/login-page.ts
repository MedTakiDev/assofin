import { Component, inject, signal } from '@angular/core';
import { AuthService } from '@auth/services/auth.service';
import { Router } from '@angular/router';
import { LoginRequestDto } from '@auth/dto/login-request.dto';
import { NotificationService } from '@core/services/notification.service';

@Component({
  imports: [],
  selector: 'app-login-page',
  styleUrl: './login-page.css',
  templateUrl: './login-page.html',
})
export class LoginPage {
  // View state signals
  isLoading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);
  private readonly authService = inject(AuthService);
  private readonly notificationService = inject(NotificationService);
  private readonly router = inject(Router);

  /**
   * Receives credentials from LoginForm child and executes auth request
   */
  onLogin(credentials: LoginRequestDto): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    console.log(credentials);
    this.authService.login(credentials).subscribe({
      next: (data) => {
        this.isLoading.set(false);
        console.log(data.message);
        this.router.navigateByUrl('/'); //navigate user to the main view
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(err.error?.message || 'Invalid email or password. Please try again.');
        this.notificationService.show(err.message);
      },
    });
  }
}
