/**
 * Sidebar Navigation Component
 */

import { NavLink, useNavigate } from 'react-router-dom';
import { Menu, X } from 'lucide-react';
import { useState } from 'react';

interface SidebarProps {
  onLogout: () => void;
  tenantName?: string;
}

export const Sidebar = ({ onLogout, tenantName }: SidebarProps) => {
  const [isOpen, setIsOpen] = useState(false);
  const navigate = useNavigate();

  const handleLogout = () => {
    onLogout();
    navigate('/login');
  };

  const navItems = [
    { path: '/dashboard', label: 'Dashboard', icon: '📊' },
    { path: '/templates', label: 'Templates', icon: '📋' },
    { path: '/policies', label: 'Routing Policies', icon: '🛣️' },
    { path: '/test-delivery', label: 'Test Delivery', icon: '✉️' }
  ];

  const getLinkClass = ({ isActive }: { isActive: boolean }) => {
    return `flex items-center gap-3 px-4 py-2 rounded-lg transition-colors ${
      isActive 
        ? 'bg-slate-100 text-slate-900 font-medium' 
        : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'
    }`;
  };

  return (
    <>
      {/* Mobile toggle */}
      <button
        className="md:hidden fixed top-4 left-4 z-50 p-2 rounded-lg bg-slate-900 text-white hover:bg-slate-800"
        onClick={() => setIsOpen(!isOpen)}
      >
        {isOpen ? <X size={24} /> : <Menu size={24} />}
      </button>

      {/* Overlay for mobile */}
      {isOpen && (
        <div
          className="fixed inset-0 bg-black/50 md:hidden z-30"
          onClick={() => setIsOpen(false)}
        />
      )}

      {/* Sidebar */}
      <aside
        className={`
          fixed md:sticky top-0 left-0 z-40 h-screen w-64 bg-white border-r border-slate-200
          transform transition-transform md:transform-none
          ${isOpen ? 'translate-x-0' : '-translate-x-full md:translate-x-0'}
          flex flex-col
        `}
      >
        {/* Header */}
        <div className="border-b border-slate-200 p-4">
          <h1 className="text-xl font-bold text-slate-900">PulseRelay</h1>
          {tenantName && (
            <p className="text-xs text-slate-500 mt-1 truncate">{tenantName}</p>
          )}
        </div>

        {/* Navigation */}
        <nav className="flex-1 overflow-y-auto p-4 space-y-1">
          {navItems.map(item => (
            <NavLink
              key={item.path}
              to={item.path}
              className={getLinkClass}
              onClick={() => setIsOpen(false)}
            >
              <span className="text-lg">{item.icon}</span>
              <span>{item.label}</span>
            </NavLink>
          ))}
        </nav>

        {/* Footer */}
        <div className="border-t border-slate-200 p-4">
          <button
            onClick={handleLogout}
            className="w-full px-4 py-2 rounded-lg bg-slate-100 text-slate-900 hover:bg-slate-200 font-medium text-sm transition-colors"
          >
            Sign Out
          </button>
        </div>
      </aside>
    </>
  );
};
