/**
 * API Request/Response Types
 * Based on backend API audit
 */

// ===== Enums =====

export type Channel = 'EMAIL' | 'SMS' | 'PUSH' | 'IN_APP';
export type Priority = 'CRITICAL' | 'HIGH' | 'NORMAL' | 'LOW';
export type DeliveryStatus = 'ACCEPTED' | 'DELIVERED' | 'FAILED' | 'PERMANENT_FAILURE' | 'RETRY_SCHEDULED';
export type NotificationStatus = 'PENDING' | 'PROCESSING' | 'RETRYING' | 'DELIVERED' | 'FAILED';
export type TenantStatus = 'ACTIVE' | 'SUSPENDED';

// ===== Tenant =====

export interface TenantInfo {
  tenantId: string;
  organizationName: string;
  contactEmail: string;
  status: TenantStatus;
}

export interface TenantRegistrationRequest {
  organizationName: string;
  contactEmail: string;
}

export interface TenantRegistrationResponse extends TenantInfo {
  createdAt: string;
  apiKey: string;
  apiKeyWarning: string;
}

// ===== Notifications =====

export interface NotificationRequest {
  eventType: string;
  recipientEmail?: string;
  recipientPhone?: string;
  recipientUserId?: string;
  requestedChannels: string;
  priority: Priority;
  payload?: Record<string, unknown>;
}

export interface NotificationResponse {
  notificationId: string;
  status: NotificationStatus;
  message: string;
  createdAt: string;
}

export interface TestDeliveryRequest {
  channel: Channel;
  recipient: string;
  subject?: string;
  body: string;
}

export interface DeliveryAttempt {
  id: string;
  status: DeliveryStatus;
  channel: Channel;
  provider: string;
  recipient: string;
  providerMessageId: string | null;
  errorCode: string | null;
  errorMessage: string | null;
  isRetryable: boolean;
  attemptedAt: string;
  nextAttemptAt: string | null;
}

// ===== Templates =====

export interface NotificationTemplate {
  id: string;
  name: string;
  eventType: string;
  channel: Channel;
  subject: string | null;
  body: string;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateTemplateRequest {
  name: string;
  eventType: string;
  channel: Channel;
  subject?: string;
  body: string;
  enabled: boolean;
}

export interface UpdateTemplateRequest extends CreateTemplateRequest {}

export interface TemplateRenderRequest {
  templateId: string;
  variables?: Record<string, unknown>;
}

export interface TemplateRenderResponse {
  templateId: string;
  renderedSubject: string;
  renderedBody: string;
}

// ===== Routing Policies =====

export interface RoutingPolicy {
  id: string;
  eventType: string;
  primaryChannels: string;
  fallbackChannels: string | null;
  priority: Priority;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateRoutingPolicyRequest {
  eventType: string;
  primaryChannels: string;
  fallbackChannels?: string;
  priority: Priority;
  enabled?: boolean;
}

export interface UpdateRoutingPolicyRequest extends CreateRoutingPolicyRequest {}

// ===== Error Responses =====

export interface ApiError {
  error: string;
  status: number;
}

// ===== Generic =====

export interface ApiResponse<T> {
  data?: T;
  error?: ApiError;
  loading: boolean;
}
