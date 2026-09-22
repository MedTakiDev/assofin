import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, catchError, throwError } from 'rxjs';

import { APP_CONFIG } from '@core/config/app.config';

@Injectable({
  providedIn: 'root',
})
export class ApiService {
  private readonly config = inject(APP_CONFIG);
  private readonly http = inject(HttpClient);

  private get baseUrl(): string {
    return this.config.apiUrl;
  }

  get<T>(
    path: string,
    params: HttpParams = new HttpParams(),
  ): Observable<T> {
    return this.http
      .get<T>(`${this.baseUrl}/${path}`, { params })
      .pipe(catchError(this.formatErrors));
  }

  post<T>(
    path: string,
    body: unknown = {},
  ): Observable<T> {
    return this.http
      .post<T>(`${this.baseUrl}/${path}`, body)
      .pipe(catchError(this.formatErrors));
  }

  put<T>(
    path: string,
    body: unknown = {},
  ): Observable<T> {
    return this.http
      .put<T>(`${this.baseUrl}/${path}`, body)
      .pipe(catchError(this.formatErrors));
  }

  delete<T>(path: string): Observable<T> {
    return this.http
      .delete<T>(`${this.baseUrl}/${path}`)
      .pipe(catchError(this.formatErrors));
  }

  private formatErrors(error: any): Observable<never> {
    console.error('API Error Occurred:', error);

    return throwError(() =>
        error?.error ?? {
          message: 'A network error occurred.',
        },
    );
  }
}
