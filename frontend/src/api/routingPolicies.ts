import { apiClient } from './client';

export interface RoutingPolicy {
  id: string;
  eventType: string;
  primaryChannels: string;
  fallbackChannels: string | null;
  priority: string;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateRoutingPolicyRequest {
  eventType: string;
  primaryChannels: string;
  fallbackChannels?: string;
  priority: string;
  enabled?: boolean;
}

export interface UpdateRoutingPolicyRequest extends CreateRoutingPolicyRequest {}

export const routingPoliciesApi = {
  async listPolicies(): Promise<RoutingPolicy[]> {
    const response = await apiClient.getClient().get<RoutingPolicy[]>(
      '/v1/routing-policies'
    );
    return response.data;
  },

  async getPolicy(policyId: string): Promise<RoutingPolicy> {
    const response = await apiClient.getClient().get<RoutingPolicy>(
      `/v1/routing-policies/${policyId}`
    );
    return response.data;
  },

  async createPolicy(request: CreateRoutingPolicyRequest): Promise<RoutingPolicy> {
    const response = await apiClient.getClient().post<RoutingPolicy>(
      '/v1/routing-policies',
      request
    );
    return response.data;
  },

  async updatePolicy(
    policyId: string,
    request: UpdateRoutingPolicyRequest
  ): Promise<RoutingPolicy> {
    const response = await apiClient.getClient().put<RoutingPolicy>(
      `/v1/routing-policies/${policyId}`,
      request
    );
    return response.data;
  },

  async enablePolicy(policyId: string): Promise<RoutingPolicy> {
    const response = await apiClient.getClient().patch<RoutingPolicy>(
      `/v1/routing-policies/${policyId}/enable`
    );
    return response.data;
  },

  async disablePolicy(policyId: string): Promise<RoutingPolicy> {
    const response = await apiClient.getClient().patch<RoutingPolicy>(
      `/v1/routing-policies/${policyId}/disable`
    );
    return response.data;
  },

  async deletePolicy(policyId: string): Promise<void> {
    await apiClient.getClient().delete(`/v1/routing-policies/${policyId}`);
  },
};
