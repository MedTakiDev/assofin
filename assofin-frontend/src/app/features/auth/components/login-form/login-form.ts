import {Component, computed, output, signal} from '@angular/core';
import {IconField} from 'primeng/iconfield';
import {InputIcon} from 'primeng/inputicon';
import {InputPassword} from 'primeng/inputpassword';
import {FormGroup, FormsModule} from '@angular/forms';
import {InputText} from 'primeng/inputtext';
import { Eye } from '@primeicons/angular/eye';
import { EyeSlash } from '@primeicons/angular/eye-slash';
import {ButtonModule} from 'primeng/button';
import { User } from '@primeicons/angular/user';
import {LoginRequestDto} from '@auth/dto/login-request.dto';
@Component({
  imports: [
    IconField,
    InputIcon,
    InputPassword,
    FormsModule,
    InputText,
    User,
    Eye,
    EyeSlash,

    ButtonModule,
  ],  selector: 'app-login-form',
  styleUrl: './login-form.css',
  templateUrl: './login-form.html',
})
export class LoginForm {
   email = signal<string>('');
   password = signal<string>('');
   mask = signal<boolean>(true);
   requestSignup = output<void>();

  isFormInvalid = computed(() => {
    return !this.email().trim() || !this.password().trim();
  });

  toggleMask() {
    this.mask.update((v) => !v);
  }

  public signIn() {
    // Construct your payload DTO on-demand for the API call
    const payload: LoginRequestDto = {
      email: this.email(),
      password: this.password(),
    };
    console.log('Sending payload:', payload);
  }
}
