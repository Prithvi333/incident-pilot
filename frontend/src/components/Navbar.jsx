import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const NAV_ITEMS = [
  { path: '/', label: 'Dashboard' },
  { path: '/incidents', label: 'Incidents' },
  { path: '/incidents/new', label: 'Create Incident' },
  { path: '/documents', label: 'Knowledge Base' },
];

export default function Navbar() {
  const location = useLocation();
  const { user, logout } = useAuth();

  return (
    <nav className="bg-gray-900 text-white shadow-lg">
      <div className="max-w-7xl mx-auto px-4">
        <div className="flex items-center justify-between h-14">
          <Link to="/" className="text-lg font-bold tracking-tight">
            🔥 IncidentPilot
          </Link>
          <div className="flex items-center gap-1">
            {NAV_ITEMS.map(({ path, label }) => (
              <Link
                key={path}
                to={path}
                className={`px-3 py-2 rounded text-sm font-medium transition-colors ${
                  location.pathname === path
                    ? 'bg-gray-700 text-white'
                    : 'text-gray-300 hover:bg-gray-800 hover:text-white'
                }`}
              >
                {label}
              </Link>
            ))}
            {user && (
              <>
                <span className="ml-4 text-xs text-gray-400">
                  {user.username} ({user.role})
                </span>
                <button
                  onClick={logout}
                  className="ml-2 px-3 py-1.5 text-xs text-gray-300 hover:text-white hover:bg-gray-800 rounded transition-colors"
                >
                  Logout
                </button>
              </>
            )}
          </div>
        </div>
      </div>
    </nav>
  );
}
