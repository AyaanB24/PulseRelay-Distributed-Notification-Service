import { useState, useEffect } from 'react';
import { Button } from '../components/ui/Button';
import { useAuth } from '../context/AuthContext';
import { tenantApi } from '../api/tenant';
import { TenantInfo } from '../api/tenant';

export function Dashboard() {
  const { logout } = useAuth();
  const [tenantInfo, setTenantInfo] = useState<TenantInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchTenantInfo = async () => {
      try {
        const info = await tenantApi.getTenantInfo();
        setTenantInfo(info);
      } catch (err) {
        setError('Failed to load tenant information');
        console.error(err);
      } finally {
        setLoading(false);
      }
    };

    fetchTenantInfo();
  }, []);

  return (
    <div className="min-h-screen bg-white">
      {/* Header */}
      <header className="border-b border-neutral-200">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-4 flex justify-between items-center">
          <h1 className="text-2xl font-semibold text-neutral-950">
            PulseRelay Dashboard
          </h1>
          <Button variant="outline" onClick={logout}>
            Sign Out
          </Button>
        </div>
      </header>

      {/* Main content */}
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {loading ? (
          <div className="text-center py-12">
            <p className="text-neutral-500">Loading...</p>
          </div>
        ) : error ? (
          <div className="bg-red-50 border border-red-200 rounded-lg p-4">
            <p className="text-red-800">{error}</p>
          </div>
        ) : tenantInfo ? (
          <div className="space-y-8">
            {/* Tenant info card */}
            <div className="bg-neutral-50 border border-neutral-200 rounded-lg p-6">
              <h2 className="text-lg font-semibold text-neutral-950 mb-4">
                Account Information
              </h2>
              <dl className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <div>
                  <dt className="text-sm font-medium text-neutral-500">
                    Organization
                  </dt>
                  <dd className="mt-2 text-base text-neutral-950">
                    {tenantInfo.organizationName}
                  </dd>
                </div>
                <div>
                  <dt className="text-sm font-medium text-neutral-500">
                    Contact Email
                  </dt>
                  <dd className="mt-2 text-base text-neutral-950">
                    {tenantInfo.contactEmail}
                  </dd>
                </div>
                <div>
                  <dt className="text-sm font-medium text-neutral-500">
                    Status
                  </dt>
                  <dd className="mt-2">
                    <span className={`px-3 py-1 rounded-full text-sm font-medium ${
                      tenantInfo.status === 'ACTIVE'
                        ? 'bg-green-50 text-green-700'
                        : 'bg-red-50 text-red-700'
                    }`}>
                      {tenantInfo.status}
                    </span>
                  </dd>
                </div>
                <div>
                  <dt className="text-sm font-medium text-neutral-500">
                    Tenant ID
                  </dt>
                  <dd className="mt-2 text-sm text-neutral-600 font-mono break-all">
                    {tenantInfo.tenantId}
                  </dd>
                </div>
              </dl>
            </div>

            {/* Features grid */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="bg-neutral-50 border border-neutral-200 rounded-lg p-6">
                <h3 className="text-base font-semibold text-neutral-950 mb-2">
                  Templates
                </h3>
                <p className="text-sm text-neutral-500 mb-4">
                  Manage notification templates for different event types
                </p>
                <Button variant="outline" className="w-full">
                  Manage Templates
                </Button>
              </div>

              <div className="bg-neutral-50 border border-neutral-200 rounded-lg p-6">
                <h3 className="text-base font-semibold text-neutral-950 mb-2">
                  Routing Policies
                </h3>
                <p className="text-sm text-neutral-500 mb-4">
                  Configure how notifications are routed across channels
                </p>
                <Button variant="outline" className="w-full">
                  Manage Policies
                </Button>
              </div>

              <div className="bg-neutral-50 border border-neutral-200 rounded-lg p-6">
                <h3 className="text-base font-semibold text-neutral-950 mb-2">
                  Test Delivery
                </h3>
                <p className="text-sm text-neutral-500 mb-4">
                  Send test notifications to verify configuration
                </p>
                <Button variant="outline" className="w-full">
                  Test Delivery
                </Button>
              </div>

              <div className="bg-neutral-50 border border-neutral-200 rounded-lg p-6">
                <h3 className="text-base font-semibold text-neutral-950 mb-2">
                  API Key
                </h3>
                <p className="text-sm text-neutral-500 mb-4">
                  View and manage your API keys
                </p>
                <Button variant="outline" className="w-full">
                  Manage Keys
                </Button>
              </div>
            </div>
          </div>
        ) : null}
      </main>
    </div>
  );
}
