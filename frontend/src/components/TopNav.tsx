/**
 * Top Navigation Bar Component
 */

import { TenantInfo } from '../types/api';

interface TopNavProps {
  tenantInfo: TenantInfo | null;
}

export const TopNav = ({ tenantInfo }: TopNavProps) => {
  return (
    <header className="sticky top-0 z-30 border-b border-slate-200 bg-white">
      <div className="container-tight flex items-center justify-between py-4">
        <div>
          <h2 className="text-lg font-semibold text-slate-900">
            {tenantInfo?.organizationName || 'PulseRelay'}
          </h2>
          <p className="text-sm text-slate-500">{tenantInfo?.contactEmail}</p>
        </div>

        <div className="text-right">
          <span
            className={`inline-block px-3 py-1 rounded-full text-xs font-medium ${
              tenantInfo?.status === 'ACTIVE'
                ? 'bg-green-100 text-green-800'
                : 'bg-red-100 text-red-800'
            }`}
          >
            {tenantInfo?.status || 'UNKNOWN'}
          </span>
        </div>
      </div>
    </header>
  );
};
