import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { LoginRequestDto } from '../dto/login-request.dto';
import { AuthUserProfileDto } from '../dto/auth-user-profile.dto';
import { ApiService } from '@core/services/api.service';
import { AuthResponseDto } from '@auth/dto/auth-response.dto';

export interface TempTokenResponse {
  token: string;
}

@Injectable({ providedIn: 'root' })
export class AuthRepository {
  //--------------------------- the above implementation is used to avoid eager api call by httpresource each time the service initialised
  private readonly api = inject(ApiService);

  /**
   * Retrieves current active user session profile.
   */
  getSession(): Observable<AuthUserProfileDto> {
    // Relative path clean of version prefixing
    return this.api.get<AuthUserProfileDto>('users/me');
  }

  /**
   * Submits credentials for authentication.
   */
  login(credentials: LoginRequestDto): Observable<AuthResponseDto> {
    return this.api.post<AuthResponseDto>('auth/login', credentials);
  }

  /**
   * Terminates active session on the backend.
   */
  logout(): Observable<void> {
    return this.api.post<void>('auth/logout', {});
  }
}
