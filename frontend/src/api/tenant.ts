import { apiClient } from './client';

export interface TenantInfo {
  tenantId: string;
  organizationName: string;
  contactEmail: string;
  status: string;
}

export interface TenantRegistrationRequest {
  organizationName: string;
  contactEmail: string;
}

export interface TenantRegistrationResponse {
  tenantId: string;
  organizationName: string;
  contactEmail: string;
  status: string;
  createdAt: string;
  apiKey: string;
  apiKeyWarning: string;
}

export const tenantApi = {
  async getTenantInfo(): Promise<TenantInfo> {
    const response = await apiClient.getClient().get<TenantInfo>('/v1/tenant/me');
    return response.data;
  },

  async registerTenant(
    adminSecret: string,
    request: TenantRegistrationRequest
  ): Promise<TenantRegistrationResponse> {
    const response = await apiClient.getClient().post<TenantRegistrationResponse>(
      '/v1/admin/tenants',
      request,
      {
        headers: {
          'X-ADMIN-SECRET': adminSecret,
        },
      }
    );
    return response.data;
  },
};
