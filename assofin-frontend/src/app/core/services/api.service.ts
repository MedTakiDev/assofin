import { Injectable, inject } from '@angular/core';
import {HttpClient, HttpContext, HttpHeaders, HttpParams} from '@angular/common/http';
import { Observable, catchError, throwError } from 'rxjs';

import { APP_CONFIG } from '@core/config/app.config';

export interface ApiOptions {
  params?: HttpParams;
  headers?: HttpHeaders;
  context?: HttpContext;
  withCredentials?: boolean;
}

@Injectable({
  providedIn: 'root',
})
export class ApiService {

  private readonly config = inject(APP_CONFIG);
  private readonly http = inject(HttpClient);

  // Best Approach for Versioning: Treat API versioning as a structural segment config.
  private readonly API_VERSION = 'api/v1';

  /**
   * Computes the foundational URL cleanly ensuring no trailing slashes collide.
   */
  private get apiUrl(): string {
    const base = this.config.apiUrl.replace(/\/\$/, '');
    return `${base}/${this.API_VERSION}`;
  }

  get<T>(path: string, options: ApiOptions = {}): Observable<T> {
    // Defaulting global cookie credentials for auth flows, can be explicitly overriden
    return this.http
      .get<T>(`${this.apiUrl}/${path}`, { withCredentials: true, ...options })
      .pipe(catchError(this.formatErrors));
  }

  post<T>(path: string, body: unknown = {}, options: ApiOptions = {}): Observable<T> {
    return this.http
      .post<T>(`${this.apiUrl}/${path}`, body, { withCredentials: true, ...options })
      .pipe(catchError(this.formatErrors));
  }

  put<T>(path: string, body: unknown = {}, options: ApiOptions = {}): Observable<T> {
    return this.http
      .put<T>(`${this.apiUrl}/${path}`, body, { withCredentials: true, ...options })
      .pipe(catchError(this.formatErrors));
  }

  delete<T>(path: string, options: ApiOptions = {}): Observable<T> {
    return this.http
      .delete<T>(`${this.apiUrl}/${path}`, { withCredentials: true, ...options })
      .pipe(catchError(this.formatErrors));
  }

  private formatErrors(error: any): Observable<never> {
    console.error('API Error Occurred:', error);
    return throwError(() => error?.error ?? { message: 'A network error occurred.' });
  }
}
