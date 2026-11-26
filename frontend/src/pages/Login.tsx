import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { useAuth } from '../context/AuthContext';

export function Login() {
  const [apiKey, setApiKey] = useState('');
  const [error, setError] = useState('');
  const navigate = useNavigate();
  const { setApiKey: setAuthApiKey } = useAuth();

  const handleLogin = (e: React.FormEvent) => {
    e.preventDefault();
    if (!apiKey.trim()) {
      setError('API key is required');
      return;
    }
    setAuthApiKey(apiKey);
    navigate('/dashboard');
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-neutral-50">
      <div className="w-full max-w-md">
        <div className="bg-white rounded-lg border border-neutral-200 p-8">
          <h1 className="text-2xl font-semibold text-neutral-950 mb-2">
            PulseRelay
          </h1>
          <p className="text-neutral-500 mb-8">
            Notification Dashboard
          </p>

          <form onSubmit={handleLogin} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-neutral-950 mb-2">
                API Key
              </label>
              <Input
                type="password"
                placeholder="pr_live_..."
                value={apiKey}
                onChange={(e) => {
                  setApiKey(e.target.value);
                  setError('');
                }}
              />
            </div>

            {error && (
              <p className="text-sm text-red-600">{error}</p>
            )}

            <Button type="submit" className="w-full">
              Sign In
            </Button>
          </form>

          <p className="text-sm text-neutral-500 text-center mt-6">
            Don't have an API key? Contact your administrator.
          </p>
        </div>
      </div>
    </div>
  );
}
