/**
 * Root component — sets up client-side routing and authentication.
 *
 * Public routes (login/register) are outside the ProtectedRoute wrapper.
 * All other routes require a valid JWT and render inside the sidebar layout.
 */
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import ProtectedRoute from './components/ProtectedRoute';
import Sidebar from './components/Sidebar';
import Dashboard from './pages/Dashboard';
import IncidentList from './pages/IncidentList';
import IncidentDetail from './pages/IncidentDetail';
import CreateIncident from './pages/CreateIncident';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import DocumentUpload from './pages/DocumentUpload';

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route
            path="/*"
            element={
              <ProtectedRoute>
                <div className="flex h-screen bg-zinc-900 text-zinc-100 overflow-hidden">
                  <Sidebar />
                  <main className="flex-1 overflow-y-auto">
                    <div className="max-w-[1400px] mx-auto px-6 lg:px-10 py-8">
                      <Routes>
                        <Route path="/" element={<Dashboard />} />
                        <Route path="/incidents" element={<IncidentList />} />
                        <Route path="/incidents/new" element={<CreateIncident />} />
                        <Route path="/incidents/:id" element={<IncidentDetail />} />
                        <Route path="/documents" element={<DocumentUpload />} />
                      </Routes>
                    </div>
                  </main>
                </div>
              </ProtectedRoute>
            }
          />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}
