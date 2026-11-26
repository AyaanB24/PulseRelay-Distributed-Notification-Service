import React, { createContext, useContext, useState, useEffect } from 'react';
import { apiClient } from '../api/client';
import { TenantInfo } from '../api/tenant';

interface AuthContextType {
  isAuthenticated: boolean;
  apiKey: string | null;
  tenantInfo: TenantInfo | null;
  setApiKey: (key: string) => void;
  logout: () => void;
  loading: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [apiKey, setApiKeyState] = useState<string | null>(null);
  const [tenantInfo, setTenantInfo] = useState<TenantInfo | null>(null);
  const [loading, setLoading] = useState(true);

  // Check if API key exists on mount
  useEffect(() => {
    const storedKey = localStorage.getItem('apiKey');
    if (storedKey) {
      apiClient.setApiKey(storedKey);
      setApiKeyState(storedKey);
      setIsAuthenticated(true);
    }
    setLoading(false);
  }, []);

  const setApiKey = (key: string) => {
    apiClient.setApiKey(key);
    setApiKeyState(key);
    setIsAuthenticated(true);
  };

  const logout = () => {
    apiClient.clearApiKey();
    setApiKeyState(null);
    setIsAuthenticated(false);
    setTenantInfo(null);
    localStorage.removeItem('apiKey');
  };

  const value = {
    isAuthenticated,
    apiKey,
    tenantInfo,
    setApiKey,
    logout,
    loading,
  };

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return context;
}
