/**
 * useAuth Hook
 * Manages authentication state and operations
 */

import { useState, useEffect, useCallback } from 'react';
import { httpClient } from '../services/http';
import { TenantInfo } from '../types/api';
import { apiEndpoints } from '../config/api';

export const useAuth = () => {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [tenantInfo, setTenantInfo] = useState<TenantInfo | null>(null);
  const [loading, setLoading] = useState(true);

  // Initialize auth on mount
  useEffect(() => {
    const initAuth = async () => {
      try {
        if (httpClient.hasAuth()) {
          setIsAuthenticated(true);
          // Fetch tenant info to verify auth
          const info = await httpClient.get<TenantInfo>(apiEndpoints.tenant.me);
          setTenantInfo(info);
        }
      } catch (err) {
        httpClient.clearAuth();
        setIsAuthenticated(false);
        setTenantInfo(null);
      } finally {
        setLoading(false);
      }
    };

    initAuth();
  }, []);

  const login = useCallback((apiKey: string) => {
    httpClient.setApiKey(apiKey);
    setIsAuthenticated(true);
  }, []);

  const logout = useCallback(() => {
    httpClient.clearAuth();
    setIsAuthenticated(false);
    setTenantInfo(null);
  }, []);

  return {
    isAuthenticated,
    tenantInfo,
    loading,
    login,
    logout
  };
};
