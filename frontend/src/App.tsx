import { Navigate, Route, Routes, useParams } from 'react-router-dom'
import { Layout } from './components/Layout'
import { NotFoundPage } from './pages/NotFoundPage'
import { UserFormPage } from './pages/users/UserFormPage'
import { UsersPage } from './pages/users/UsersPage'

// Remount the form when the id changes so no state from the previous user survives.
function UserEditRoute() {
  const { id } = useParams()
  return <UserFormPage key={id} />
}

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Navigate to="/events" replace />} />
        <Route path="users" element={<UsersPage />} />
        <Route path="users/new" element={<UserFormPage />} />
        <Route path="users/:id" element={<UserEditRoute />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
