import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { HttpResourceRequest } from '@angular/common/http';
import { Observable } from 'rxjs';
import {LoginRequestDto} from '../dto/login-request.dto';
import {UserProfileDto} from '../dto/user-profile.dto';
import {RegisterRequestDto} from '../dto/register-request.dto';
import {ApiService} from '@core/services/api.service';

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
  getSession(): Observable<UserProfileDto> {
    // Relative path clean of version prefixing
    return this.api.get<UserProfileDto>('auth/me');
  }

  /**
   * Submits credentials for authentication.
   */
  login(credentials: LoginRequestDto): Observable<UserProfileDto> {
    return this.api.post<UserProfileDto>('auth/login', credentials);
  }


  loginToken(credentials: LoginRequestDto): Observable<TempTokenResponse> {
    return this.api.post<TempTokenResponse>('auth/login', credentials);
  }
  /**
   * Submits registration details for new account creation.
   */
  signup(payload: RegisterRequestDto): Observable<UserProfileDto> {
    return this.api.post<UserProfileDto>('auth/register', payload);
  }

  /**
   * Terminates active session on the backend.
   */
  logout(): Observable<void> {
    return this.api.post<void>('auth/logout', {});
  }

}
