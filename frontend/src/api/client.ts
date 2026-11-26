import axios, { AxiosInstance, AxiosError } from 'axios';

export interface ApiConfig {
  baseURL?: string;
  apiKey?: string;
}

class ApiClient {
  private client: AxiosInstance;
  private apiKey: string | null = null;

  constructor(config: ApiConfig = {}) {
    this.apiKey = config.apiKey || localStorage.getItem('apiKey');

    this.client = axios.create({
      baseURL: config.baseURL || '/api',
      headers: {
        'Content-Type': 'application/json',
      },
    });

    // Add request interceptor to include API key
    this.client.interceptors.request.use((config) => {
      if (this.apiKey) {
        config.headers['X-API-KEY'] = this.apiKey;
      }
      return config;
    });

    // Response interceptor for error handling
    this.client.interceptors.response.use(
      (response) => response,
      (error: AxiosError) => {
        if (error.response?.status === 401) {
          // Unauthorized - clear stored API key
          localStorage.removeItem('apiKey');
          this.apiKey = null;
          window.location.href = '/';
        }
        return Promise.reject(error);
      }
    );
  }

  setApiKey(key: string) {
    this.apiKey = key;
    localStorage.setItem('apiKey', key);
  }

  clearApiKey() {
    this.apiKey = null;
    localStorage.removeItem('apiKey');
  }

  hasApiKey(): boolean {
    return this.apiKey !== null;
  }

  getApiKey(): string | null {
    return this.apiKey;
  }

  getClient(): AxiosInstance {
    return this.client;
  }
}

export const apiClient = new ApiClient();
