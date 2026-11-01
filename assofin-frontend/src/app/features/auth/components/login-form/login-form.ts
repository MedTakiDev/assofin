import { Component, inject, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { LoginRequestDto } from '@auth/dto/login-request.dto';

@Component({
  selector: 'app-login-form',
  imports: [
    ReactiveFormsModule, // <-- Added ReactiveFormsModule (Removed FormsModule)
    ButtonModule,
  ],
  styleUrl: './login-form.css',
  templateUrl: './login-form.html',
})
export class LoginForm {
  // UI state signals
  mask = signal<boolean>(true);
  submitLogin = output<LoginRequestDto>();
  private readonly fb = inject(FormBuilder);
  // Reactive Form Definition
  loginForm = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  toggleMask(): void {
    this.mask.update((v) => !v);
  }

  public signIn(event: Event): void {
    // Explicitly prevent standard browser GET navigation
    event.preventDefault();

    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      return;
    }

    const authCredentials: LoginRequestDto = this.loginForm.getRawValue();
    this.submitLogin.emit(authCredentials);
  }
}
