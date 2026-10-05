import {Component, output} from '@angular/core';

@Component({
  imports: [],
  selector: 'app-signup-form',
  styleUrl: './signup-form.css',
  templateUrl: './signup-form.html',
})
export class SignupForm {
  requestLogin = output<void>();

}
