/**
 * Login Page
 * Authenticates user with API key
 */

import { useState } from 'react';
import { useNavigate } from 'react-router-dom';

interface LoginPageProps {
  onLogin: (apiKey: string) => void;
}

export const LoginPage = ({ onLogin }: LoginPageProps) => {
  const [apiKey, setApiKey] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    if (!apiKey.trim()) {
      setError('API key is required');
      setLoading(false);
      return;
    }

    try {
      onLogin(apiKey);
      navigate('/dashboard');
    } catch (err) {
      setError('Failed to authenticate. Please check your API key.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-slate-50 to-slate-100">
      <div className="w-full max-w-md">
        {/* Card */}
        <div className="card">
          {/* Header */}
          <div className="mb-6 text-center">
            <h1 className="text-3xl font-bold text-slate-900">PulseRelay</h1>
            <p className="mt-2 text-slate-500">Notification Dashboard</p>
          </div>

          {/* Form */}
          <form onSubmit={handleSubmit} className="space-y-4">
            {/* API Key Input */}
            <div>
              <label htmlFor="apiKey" className="text-label">
                API Key
              </label>
              <input
                id="apiKey"
                type="password"
                placeholder="pr_live_..."
                value={apiKey}
                onChange={(e) => {
                  setApiKey(e.target.value);
                  setError('');
                }}
                className="input-field w-full mt-1"
                disabled={loading}
              />
            </div>

            {/* Error Message */}
            {error && (
              <div className="p-3 rounded-lg bg-red-50 border border-red-200">
                <p className="text-sm text-red-800">{error}</p>
              </div>
            )}

            {/* Submit Button */}
            <button
              type="submit"
              disabled={loading}
              className="btn-primary w-full"
            >
              {loading ? 'Signing in...' : 'Sign In'}
            </button>
          </form>

          {/* Footer */}
          <div className="mt-6 pt-6 border-t border-slate-200">
            <p className="text-xs text-slate-500 text-center">
              Don't have an API key?{' '}
              <span className="font-medium text-slate-700">
                Contact your administrator
              </span>
            </p>
          </div>
        </div>

        {/* Help Text */}
        <p className="mt-4 text-center text-xs text-slate-500">
          For development, use the admin secret to register a tenant:
          <br />
          <code className="block mt-2 p-2 bg-slate-800 text-slate-100 rounded text-xs">
            curl -X POST http://localhost:8084/api/v1/admin/tenants
          </code>
        </p>
      </div>
    </div>
  );
};
