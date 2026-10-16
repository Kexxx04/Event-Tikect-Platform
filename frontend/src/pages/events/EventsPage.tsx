import { Link } from 'react-router-dom'
import { eventsApi } from '../../api/events'
import { ErrorAlert } from '../../components/ErrorAlert'
import { EmptyState, Loading } from '../../components/Feedback'
import { StatusBadge } from '../../components/StatusBadge'
import { useLoader } from '../../hooks/useLoader'
import { formatDateTime } from '../../utils/dates'

export function EventsPage() {
  const { data: events, error, loading, reload } = useLoader(() => eventsApi.list(), [])

  return (
    <section>
      <div className="page-header">
        <h1>Events</h1>
        <Link to="/events/new" className="button">
          New event
        </Link>
      </div>

      {loading && <Loading label="Loading events…" />}

      {!loading && error !== null && (
        <>
          <ErrorAlert error={error} />
          <button type="button" className="button-secondary" onClick={reload}>
            Retry
          </button>
        </>
      )}

      {!loading && events && events.length === 0 && (
        <EmptyState>
          <p>No events yet.</p>
          <Link to="/events/new" className="button">
            Create the first event
          </Link>
        </EmptyState>
      )}

      {!loading && events && events.length > 0 && (
        <div className="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Start</th>
                <th>End</th>
                <th className="numeric">Capacity</th>
                <th className="numeric">Registered</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {events.map((event) => (
                <tr key={event.id}>
                  <td>
                    <Link to={`/events/${event.id}`}>{event.name}</Link>
                  </td>
                  <td>{formatDateTime(event.startDate)}</td>
                  <td>{formatDateTime(event.endDate)}</td>
                  <td className="numeric">{event.capacity}</td>
                  <td className="numeric">{event.registeredCount}</td>
                  <td>
                    <StatusBadge status={event.status} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
