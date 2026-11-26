import { apiClient } from './client';

export interface NotificationResponse {
  notificationId: string;
  status: string;
  message: string;
  createdAt: string;
}

export interface TestDeliveryRequest {
  channel: string;
  recipient: string;
  subject?: string;
  body: string;
}

export interface TestDeliveryResponse {
  id: string;
  status: string;
  channel: string;
  provider: string;
  recipient: string;
  providerMessageId: string | null;
  errorCode: string | null;
  errorMessage: string | null;
  isRetryable: boolean;
  attemptedAt: string;
  nextAttemptAt?: string | null;
}

export const notificationsApi = {
  async testDelivery(request: TestDeliveryRequest): Promise<TestDeliveryResponse> {
    const response = await apiClient.getClient().post<TestDeliveryResponse>(
      '/v1/notifications/test-delivery',
      request
    );
    return response.data;
  },
};
