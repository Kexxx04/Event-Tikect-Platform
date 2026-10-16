import { Link } from 'react-router-dom'
import { registrationsApi } from '../../api/registrations'
import { ErrorAlert } from '../../components/ErrorAlert'
import { EmptyState, Loading } from '../../components/Feedback'
import { StatusBadge } from '../../components/StatusBadge'
import { useLoader } from '../../hooks/useLoader'
import { formatDateTime } from '../../utils/dates'

export function UserEventsCard({ userId }: { userId: number }) {
  const { data: events, error, loading } = useLoader(() => registrationsApi.eventsOfUser(userId), [userId])

  return (
    <div className="card">
      <h2>Events</h2>
      {loading && <Loading label="Loading events…" />}
      {!loading && <ErrorAlert error={error} />}
      {!loading && events && events.length === 0 && (
        <EmptyState>
          <p>This user is not registered for any event.</p>
        </EmptyState>
      )}
      {!loading && events && events.length > 0 && (
        <div className="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>Event</th>
                <th>Start</th>
                <th>Event status</th>
                <th>Registration</th>
              </tr>
            </thead>
            <tbody>
              {events.map((event) => (
                <tr key={event.registrationId}>
                  <td>
                    <Link to={`/events/${event.eventId}`}>{event.eventName}</Link>
                  </td>
                  <td>{formatDateTime(event.startDate)}</td>
                  <td>
                    <StatusBadge status={event.eventStatus} />
                  </td>
                  <td>
                    <StatusBadge status={event.registrationStatus} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
