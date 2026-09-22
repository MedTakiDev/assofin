import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { HttpResourceRequest } from '@angular/common/http';
import { Observable } from 'rxjs';
import {LoginRequestDto} from '../dto/login-request.dto';
import {UserProfileDto} from '../dto/user-profile.dto';
import {RegisterRequestDto} from '../dto/register-request.dto';

@Injectable({ providedIn: 'root' })
export class AuthRepository {
  private readonly http = inject(HttpClient);

  //--------------------------- the above implementation is used to avoid eager api call by httpresource each time the service initialised
  getSession(): Observable<UserProfileDto> {
    return this.http.get<UserProfileDto>('/api/v1/auth/me', {
      withCredentials: true,
    });
  }

  /**
   * Submits credentials for authentication.
   */
  login(credentials: LoginRequestDto): Observable<UserProfileDto> {
    return this.http.post<UserProfileDto>('/api/v1/auth/login', credentials, {
      withCredentials: true,
    });
  }

  /**
   * Submits registration details for new account creation.
   */
  signup(payload: RegisterRequestDto): Observable<UserProfileDto> {
    return this.http.post<UserProfileDto>('/api/v1/auth/register', payload, {
      withCredentials: true,
    });
  }

  /**
   * Terminates active session on the backend.
   */
  logout(): Observable<void> {
    return this.http.post<void>(
      '/api/v1/auth/logout',
      {},
      {
        withCredentials: true,
      },
    );
  }
}
