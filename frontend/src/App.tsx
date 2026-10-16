import { Navigate, Route, Routes, useParams } from 'react-router-dom'
import { RequireAuth } from './auth/RequireAuth'
import { Layout } from './components/Layout'
import { LoginPage } from './pages/LoginPage'
import { EventDetailPage } from './pages/events/EventDetailPage'
import { EventFormPage } from './pages/events/EventFormPage'
import { EventsPage } from './pages/events/EventsPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { UserDetailPage } from './pages/users/UserDetailPage'
import { UserFormPage } from './pages/users/UserFormPage'
import { UsersPage } from './pages/users/UsersPage'

// Remount the page when the id changes so no state from the previous user survives.
function UserDetailRoute() {
  const { id } = useParams()
  return <UserDetailPage key={id} />
}

function UserEditRoute() {
  const { id } = useParams()
  return <UserFormPage key={id} />
}

function EventEditRoute() {
  const { id } = useParams()
  return <EventFormPage key={id} />
}

export default function App() {
  return (
    <Routes>
      <Route path="login" element={<LoginPage />} />
      <Route
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route index element={<Navigate to="/events" replace />} />
        <Route path="users" element={<UsersPage />} />
        <Route path="users/new" element={<UserFormPage />} />
        <Route path="users/:id" element={<UserDetailRoute />} />
        <Route path="users/:id/edit" element={<UserEditRoute />} />
        <Route path="events" element={<EventsPage />} />
        <Route path="events/new" element={<EventFormPage />} />
        <Route path="events/:id" element={<EventDetailPage />} />
        <Route path="events/:id/edit" element={<EventEditRoute />} />
      </Route>
      {/* Outside RequireAuth and the app shell: unknown URLs show the 404 page with or without a session. */}
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}
