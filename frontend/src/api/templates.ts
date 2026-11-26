import { apiClient } from './client';

export interface NotificationTemplate {
  id: string;
  name: string;
  eventType: string;
  channel: string;
  subject: string | null;
  body: string;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateTemplateRequest {
  name: string;
  eventType: string;
  channel: string;
  subject?: string;
  body: string;
  enabled: boolean;
}

export interface UpdateTemplateRequest extends CreateTemplateRequest {}

export const templatesApi = {
  async listTemplates(): Promise<NotificationTemplate[]> {
    const response = await apiClient.getClient().get<NotificationTemplate[]>(
      '/v1/notification-templates'
    );
    return response.data;
  },

  async getTemplate(templateId: string): Promise<NotificationTemplate> {
    const response = await apiClient.getClient().get<NotificationTemplate>(
      `/v1/notification-templates/${templateId}`
    );
    return response.data;
  },

  async createTemplate(request: CreateTemplateRequest): Promise<NotificationTemplate> {
    const response = await apiClient.getClient().post<NotificationTemplate>(
      '/v1/notification-templates',
      request
    );
    return response.data;
  },

  async updateTemplate(
    templateId: string,
    request: UpdateTemplateRequest
  ): Promise<NotificationTemplate> {
    const response = await apiClient.getClient().put<NotificationTemplate>(
      `/v1/notification-templates/${templateId}`,
      request
    );
    return response.data;
  },

  async enableTemplate(templateId: string): Promise<NotificationTemplate> {
    const response = await apiClient.getClient().patch<NotificationTemplate>(
      `/v1/notification-templates/${templateId}/enable`
    );
    return response.data;
  },

  async disableTemplate(templateId: string): Promise<NotificationTemplate> {
    const response = await apiClient.getClient().patch<NotificationTemplate>(
      `/v1/notification-templates/${templateId}/disable`
    );
    return response.data;
  },

  async deleteTemplate(templateId: string): Promise<void> {
    await apiClient.getClient().delete(`/v1/notification-templates/${templateId}`);
  },
};
