/**
 * API Configuration
 * Uses environment variables for flexible deployment
 */

declare global {
  interface ImportMeta {
    readonly env: ImportMetaEnv;
  }

  interface ImportMetaEnv {
    readonly VITE_API_BASE_URL?: string;
    readonly VITE_API_CONTEXT_PATH?: string;
  }
}

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL as string) || 'http://localhost:8084';
const API_CONTEXT_PATH = (import.meta.env.VITE_API_CONTEXT_PATH as string) || '/api';

export const apiConfig = {
  baseURL: API_BASE_URL,
  contextPath: API_CONTEXT_PATH,
  fullUrl: `${API_BASE_URL}${API_CONTEXT_PATH}`,

  // Headers
  headers: {
    'Content-Type': 'application/json'
  },

  // Timeout in milliseconds
  timeout: 30000,

  // API key header name
  apiKeyHeader: 'X-API-KEY',
  adminSecretHeader: 'X-ADMIN-SECRET',
  idempotencyKeyHeader: 'Idempotency-Key'
};

export const apiEndpoints = {
  // Tenant endpoints
  tenant: {
    me: '/v1/tenant/me',
    registerAdmin: '/v1/admin/tenants'
  },

  // Notification endpoints
  notifications: {
    submit: '/v1/notifications',
    testDelivery: '/v1/notifications/test-delivery'
  },

  // Template endpoints
  templates: {
    list: '/v1/notification-templates',
    get: (id: string) => `/v1/notification-templates/${id}`,
    create: '/v1/notification-templates',
    update: (id: string) => `/v1/notification-templates/${id}`,
    delete: (id: string) => `/v1/notification-templates/${id}`,
    enable: (id: string) => `/v1/notification-templates/${id}/enable`,
    disable: (id: string) => `/v1/notification-templates/${id}/disable`,
    render: '/v1/notification-templates/render'
  },

  // Routing policy endpoints
  routingPolicies: {
    list: '/v1/routing-policies',
    get: (id: string) => `/v1/routing-policies/${id}`,
    create: '/v1/routing-policies',
    update: (id: string) => `/v1/routing-policies/${id}`,
    delete: (id: string) => `/v1/routing-policies/${id}`,
    enable: (id: string) => `/v1/routing-policies/${id}/enable`,
    disable: (id: string) => `/v1/routing-policies/${id}/disable`
  }
};
