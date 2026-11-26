/**
 * Main Application Component
 * Router setup and authentication flow
 */

import { useState, useEffect } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { httpClient } from './services/http';
import { TenantInfo } from './types/api';
import { apiEndpoints } from './config/api';

// Pages
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { TemplatesPage } from './pages/TemplatesPage';
import { PoliciesPage } from './pages/PoliciesPage';
import { TestDeliveryPage } from './pages/TestDeliveryPage';

// Components
import { Sidebar } from './components/Sidebar';
import { TopNav } from './components/TopNav';

interface ProtectedLayoutProps {
  children: React.ReactNode;
  tenantInfo: TenantInfo | null;
  onLogout: () => void;
}

const ProtectedLayout = ({ children, tenantInfo, onLogout }: ProtectedLayoutProps) => {
  return (
    <div className="flex h-screen bg-slate-50 md:pt-0 pt-16">
      <Sidebar onLogout={onLogout} tenantName={tenantInfo?.organizationName} />
      <div className="flex-1 flex flex-col overflow-hidden">
        <TopNav tenantInfo={tenantInfo} />
        <main className="flex-1 overflow-y-auto">
          {children}
        </main>
      </div>
    </div>
  );
};

const ProtectedRoute = ({ 
  children, 
  isAuthenticated, 
  isLoading 
}: { 
  children: React.ReactNode;
  isAuthenticated: boolean;
  isLoading: boolean;
}) => {
  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-slate-900 mx-auto mb-4" />
          <p className="text-slate-600">Loading...</p>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return children;
};

export const App = () => {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [tenantInfo, setTenantInfo] = useState<TenantInfo | null>(null);

  // Initialize authentication on app load
  useEffect(() => {
    const initAuth = async () => {
      try {
        if (httpClient.hasAuth()) {
          const info = await httpClient.get<TenantInfo>(apiEndpoints.tenant.me);
          setTenantInfo(info);
          setIsAuthenticated(true);
        }
      } catch (error) {
        httpClient.clearAuth();
        setIsAuthenticated(false);
      } finally {
        setIsLoading(false);
      }
    };

    initAuth();
  }, []);

  const handleLogin = (apiKey: string) => {
    httpClient.setApiKey(apiKey);
    setIsAuthenticated(true);
  };

  const handleLogout = () => {
    httpClient.clearAuth();
    setIsAuthenticated(false);
    setTenantInfo(null);
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-slate-50">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-slate-900 mx-auto mb-4" />
          <p className="text-slate-600">Loading...</p>
        </div>
      </div>
    );
  }

  return (
    <Router>
      <Routes>
        {/* Login Route - Public */}
        <Route
          path="/login"
          element={
            isAuthenticated ? (
              <Navigate to="/dashboard" replace />
            ) : (
              <LoginPage onLogin={handleLogin} />
            )
          }
        />

        {/* Protected Routes */}
        <Route
          path="/dashboard"
          element={
            <ProtectedRoute isAuthenticated={isAuthenticated} isLoading={isLoading}>
              <ProtectedLayout 
                tenantInfo={tenantInfo} 
                onLogout={handleLogout}
              >
                <DashboardPage tenantInfo={tenantInfo} />
              </ProtectedLayout>
            </ProtectedRoute>
          }
        />

        <Route
          path="/templates"
          element={
            <ProtectedRoute isAuthenticated={isAuthenticated} isLoading={isLoading}>
              <ProtectedLayout 
                tenantInfo={tenantInfo} 
                onLogout={handleLogout}
              >
                <TemplatesPage />
              </ProtectedLayout>
            </ProtectedRoute>
          }
        />

        <Route
          path="/policies"
          element={
            <ProtectedRoute isAuthenticated={isAuthenticated} isLoading={isLoading}>
              <ProtectedLayout 
                tenantInfo={tenantInfo} 
                onLogout={handleLogout}
              >
                <PoliciesPage />
              </ProtectedLayout>
            </ProtectedRoute>
          }
        />

        <Route
          path="/test-delivery"
          element={
            <ProtectedRoute isAuthenticated={isAuthenticated} isLoading={isLoading}>
              <ProtectedLayout 
                tenantInfo={tenantInfo} 
                onLogout={handleLogout}
              >
                <TestDeliveryPage />
              </ProtectedLayout>
            </ProtectedRoute>
          }
        />

        {/* Default Route */}
        <Route
          path="/"
          element={<Navigate to={isAuthenticated ? '/dashboard' : '/login'} replace />}
        />

        {/* 404 Route */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Router>
  );
};
