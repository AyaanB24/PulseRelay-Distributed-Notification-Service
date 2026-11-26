/**
 * HTTP Client Service
 * Centralized Axios instance with interceptors
 */

import axios, { AxiosInstance, AxiosError, AxiosRequestConfig } from 'axios';
import { apiConfig } from '../config/api';

class HttpClient {
  private client: AxiosInstance;
  private apiKey: string | null = null;

  constructor() {
    this.apiKey = localStorage.getItem('apiKey');

    this.client = axios.create({
      baseURL: apiConfig.fullUrl,
      timeout: apiConfig.timeout,
      headers: apiConfig.headers
    });

    // Request interceptor - add API key
    this.client.interceptors.request.use(
      (config) => {
        if (this.apiKey && !config.headers[apiConfig.adminSecretHeader]) {
          config.headers[apiConfig.apiKeyHeader] = this.apiKey;
        }
        return config;
      },
      (error) => Promise.reject(error)
    );

    // Response interceptor - handle errors
    this.client.interceptors.response.use(
      (response) => response,
      (error: AxiosError) => {
        if (error.response?.status === 401) {
          // Clear auth on 401
          this.clearAuth();
          window.location.href = '/login';
        }
        return Promise.reject(error);
      }
    );
  }

  setApiKey(key: string): void {
    this.apiKey = key;
    localStorage.setItem('apiKey', key);
  }

  clearAuth(): void {
    this.apiKey = null;
    localStorage.removeItem('apiKey');
  }

  hasAuth(): boolean {
    return this.apiKey !== null;
  }

  /**
   * GET request
   */
  get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return this.client.get<T>(url, config).then(res => res.data);
  }

  /**
   * POST request
   */
  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return this.client.post<T>(url, data, config).then(res => res.data);
  }

  /**
   * PUT request
   */
  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return this.client.put<T>(url, data, config).then(res => res.data);
  }

  /**
   * PATCH request
   */
  patch<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return this.client.patch<T>(url, data, config).then(res => res.data);
  }

  /**
   * DELETE request
   */
  delete<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return this.client.delete<T>(url, config).then(res => res.data);
  }

  /**
   * Get raw Axios instance for advanced usage
   */
  getAxiosInstance(): AxiosInstance {
    return this.client;
  }
}

export const httpClient = new HttpClient();
