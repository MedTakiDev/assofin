import { inject, Injectable } from '@angular/core';
import { MessageService, ToastMessageOptions } from 'primeng/api';

// 💡 Using 'as const' locks the values as literal types for exact PrimeNG alignment
export const messageSeverity = {
  error: "error",
  info: "info",
  warning: "warn",
  success: "success",
  secondary: "secondary",
  contrast: "contrast",
} as const;

@Injectable({ providedIn: 'root' }) // 🟢 Corrected from @Service()
export class NotificationService {
  private readonly messageService = inject(MessageService);

  show = (toastMessageOptions: ToastMessageOptions): void => {
    this.messageService.add({
      severity: toastMessageOptions.severity,
      summary: toastMessageOptions.summary,
      detail: toastMessageOptions.detail,
      life: toastMessageOptions.life || 4000 // Automatically clean toast after 4s
    });
  };
}
