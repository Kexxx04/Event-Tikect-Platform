import { Navigate, Route, Routes, useParams } from 'react-router-dom'
import { Layout } from './components/Layout'
import { EventDetailPage } from './pages/events/EventDetailPage'
import { EventFormPage } from './pages/events/EventFormPage'
import { EventsPage } from './pages/events/EventsPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { UserFormPage } from './pages/users/UserFormPage'
import { UsersPage } from './pages/users/UsersPage'

// Remount the form when the id changes so no state from the previous user survives.
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
      <Route element={<Layout />}>
        <Route index element={<Navigate to="/events" replace />} />
        <Route path="users" element={<UsersPage />} />
        <Route path="users/new" element={<UserFormPage />} />
        <Route path="users/:id" element={<UserEditRoute />} />
        <Route path="events" element={<EventsPage />} />
        <Route path="events/new" element={<EventFormPage />} />
        <Route path="events/:id" element={<EventDetailPage />} />
        <Route path="events/:id/edit" element={<EventEditRoute />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
