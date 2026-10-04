import { Component, inject, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { IconField } from 'primeng/iconfield';
import { InputIcon } from 'primeng/inputicon';
import { InputPassword } from 'primeng/inputpassword';
import { InputText } from 'primeng/inputtext';
import { ButtonModule } from 'primeng/button';
import { Eye } from '@primeicons/angular/eye';
import { EyeSlash } from '@primeicons/angular/eye-slash';
import { User } from '@primeicons/angular/user';
import { LoginRequestDto } from '@auth/dto/login-request.dto';

@Component({
  selector: 'app-login-form',
  imports: [
    IconField,
    InputIcon,
    InputPassword,
    ReactiveFormsModule, // <-- Added ReactiveFormsModule (Removed FormsModule)
    InputText,
    User,
    Eye,
    EyeSlash,
    ButtonModule,
  ],
  styleUrl: './login-form.css',
  templateUrl: './login-form.html',
})
export class LoginForm {
  private readonly fb = inject(FormBuilder);

  // UI state signals
  mask = signal<boolean>(true);

  // Component Outputs
  requestSignup = output<void>();
  submitLogin = output<LoginRequestDto>();

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
    console.log('Sending payload:', authCredentials);
    this.submitLogin.emit(authCredentials);
  }
}
