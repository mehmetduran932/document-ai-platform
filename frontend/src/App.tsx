import { Navigate, Route, BrowserRouter, Routes } from 'react-router-dom'

import { AuthProvider } from '@/auth/AuthContext'
import { AppLayout } from '@/components/AppLayout'
import { ProtectedRoute } from '@/components/ProtectedRoute'
import { AskPage } from '@/pages/AskPage'
import { ApiKeysPage } from '@/pages/ApiKeysPage'
import { DocumentsPage } from '@/pages/DocumentsPage'
import { LoginPage } from '@/pages/LoginPage'
import { RegisterPage } from '@/pages/RegisterPage'
import { SearchPage } from '@/pages/SearchPage'

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route element={<ProtectedRoute />}>
            <Route element={<AppLayout />}>
              <Route path="/documents" element={<DocumentsPage />} />
              <Route path="/search" element={<SearchPage />} />
              <Route path="/ask" element={<AskPage />} />
              <Route path="/api-keys" element={<ApiKeysPage />} />
            </Route>
          </Route>
          <Route path="/" element={<Navigate to="/documents" replace />} />
          <Route path="*" element={<Navigate to="/documents" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}

export default App
