/**
 * Dashboard Page
 * Main dashboard showing overview
 */

import { TenantInfo } from '../types/api';

interface DashboardPageProps {
  tenantInfo: TenantInfo | null;
}

export const DashboardPage = ({ tenantInfo }: DashboardPageProps) => {
  const stats = [
    { label: 'Organization', value: tenantInfo?.organizationName || '-' },
    { label: 'Contact Email', value: tenantInfo?.contactEmail || '-' },
    { label: 'Tenant ID', value: tenantInfo?.tenantId || '-', monospace: true },
    { label: 'Status', value: tenantInfo?.status || '-' }
  ];

  return (
    <div className="container-tight py-8">
      {/* Page Header */}
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-slate-900">Dashboard</h1>
        <p className="mt-2 text-slate-500">Welcome to your PulseRelay dashboard</p>
      </div>

      {/* Account Information */}
      <div className="card mb-8">
        <div className="card-header">
          <h2 className="card-title">Account Information</h2>
        </div>
        <dl className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {stats.map((stat) => (
            <div key={stat.label}>
              <dt className="text-label text-slate-700">{stat.label}</dt>
              <dd
                className={`mt-2 font-medium text-slate-900 ${
                  stat.monospace ? 'font-mono text-sm' : ''
                }`}
              >
                {stat.value}
              </dd>
            </div>
          ))}
        </dl>
      </div>

      {/* Features Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Templates Card */}
        <div className="card">
          <div className="card-header">
            <h3 className="card-title">📋 Templates</h3>
          </div>
          <p className="text-muted mb-4">
            Manage notification templates for different event types and channels
          </p>
          <a href="/templates" className="btn-primary">
            Go to Templates
          </a>
        </div>

        {/* Routing Policies Card */}
        <div className="card">
          <div className="card-header">
            <h3 className="card-title">🛣️ Routing Policies</h3>
          </div>
          <p className="text-muted mb-4">
            Configure how notifications are routed across channels
          </p>
          <a href="/policies" className="btn-primary">
            Go to Policies
          </a>
        </div>

        {/* Test Delivery Card */}
        <div className="card">
          <div className="card-header">
            <h3 className="card-title">✉️ Test Delivery</h3>
          </div>
          <p className="text-muted mb-4">
            Send test notifications to verify your configuration
          </p>
          <a href="/test-delivery" className="btn-primary">
            Test Delivery
          </a>
        </div>

        {/* API Documentation Card */}
        <div className="card">
          <div className="card-header">
            <h3 className="card-title">🔑 API Integration</h3>
          </div>
          <p className="text-muted mb-4">
            Use your API key to integrate PulseRelay into your application
          </p>
          <button className="btn-primary cursor-not-allowed opacity-50">
            Documentation (Phase 02)
          </button>
        </div>
      </div>

      {/* Quick Start */}
      <div className="card mt-8 bg-slate-50">
        <div className="card-header">
          <h3 className="card-title">🚀 Quick Start</h3>
        </div>
        <div className="space-y-3 text-sm text-slate-700">
          <p>
            <span className="font-medium">1. Create a template</span> - Define how your notifications should look
          </p>
          <p>
            <span className="font-medium">2. Set up a routing policy</span> - Choose channels for different event types
          </p>
          <p>
            <span className="font-medium">3. Test delivery</span> - Send a test notification to verify setup
          </p>
          <p>
            <span className="font-medium">4. Integrate with your app</span> - Use our API to send notifications
          </p>
        </div>
      </div>
    </div>
  );
};
