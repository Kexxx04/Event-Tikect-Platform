import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { eventsApi, NEXT_STATUSES, type EventStatus } from '../../api/events'
import { ErrorAlert } from '../../components/ErrorAlert'
import { Loading, StatusBadge, SuccessAlert } from '../../components/Feedback'
import { useLoader } from '../../hooks/useLoader'
import { formatDateTime } from '../../utils/dates'
import { AvailabilityCard } from './AvailabilityCard'

const STATUS_ACTIONS: Record<EventStatus, { label: string; className: string; confirm?: string }> = {
  SCHEDULED: { label: 'Schedule', className: 'button-secondary' },
  ACTIVE: { label: 'Activate', className: '' },
  FINISHED: { label: 'Finish', className: 'button-secondary', confirm: 'Mark this event as finished? This cannot be undone.' },
  CANCELLED: { label: 'Cancel event', className: 'button-danger', confirm: 'Cancel this event? This cannot be undone.' },
}

export function EventDetailPage() {
  const { id } = useParams()
  const eventId = Number(id)
  const { data, error, loading, reload } = useLoader(
    () => Promise.all([eventsApi.get(eventId), eventsApi.availability(eventId)]),
    [eventId],
  )
  const [actionError, setActionError] = useState<unknown>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [changing, setChanging] = useState(false)

  async function changeStatus(status: EventStatus) {
    const action = STATUS_ACTIONS[status]
    if (action.confirm && !window.confirm(action.confirm)) return
    setActionError(null)
    setNotice(null)
    setChanging(true)
    try {
      await eventsApi.updateStatus(eventId, status)
      setNotice(`Status changed to ${status}`)
      reload()
    } catch (err) {
      setActionError(err)
    } finally {
      setChanging(false)
    }
  }

  if (loading && !data) return <Loading label="Loading event…" />

  if (error || !data) {
    return (
      <section>
        <Link to="/events" className="back-link">
          ← Back to events
        </Link>
        <ErrorAlert error={error} />
      </section>
    )
  }

  const [event, availability] = data
  const nextStatuses = NEXT_STATUSES[event.status]
  const editable = nextStatuses.length > 0

  return (
    <section>
      <Link to="/events" className="back-link">
        ← Back to events
      </Link>
      <div className="page-header">
        <h1>{event.name}</h1>
        <div className="actions">
          {editable && (
            <Link to={`/events/${event.id}/edit`} className="button button-secondary">
              Edit
            </Link>
          )}
          {nextStatuses.map((status) => (
            <button
              key={status}
              type="button"
              className={STATUS_ACTIONS[status].className}
              onClick={() => changeStatus(status)}
              disabled={changing}
            >
              {STATUS_ACTIONS[status].label}
            </button>
          ))}
        </div>
      </div>

      <SuccessAlert message={notice} />
      <ErrorAlert error={actionError} />

      <div className="grid-2">
        <div className="card">
          <h2>Details</h2>
          <dl className="details">
            <dt>Status</dt>
            <dd>
              <StatusBadge status={event.status} />
            </dd>
            <dt>Start</dt>
            <dd>{formatDateTime(event.startDate)}</dd>
            <dt>End</dt>
            <dd>{formatDateTime(event.endDate)}</dd>
            <dt>Capacity</dt>
            <dd>{event.capacity}</dd>
            <dt>Registered</dt>
            <dd>{event.registeredCount}</dd>
          </dl>
          {event.description && <p className="description">{event.description}</p>}
          {!editable && <p className="field-hint">This event is {event.status} and can no longer be modified.</p>}
        </div>

        <AvailabilityCard availability={availability} />
      </div>
    </section>
  )
}
