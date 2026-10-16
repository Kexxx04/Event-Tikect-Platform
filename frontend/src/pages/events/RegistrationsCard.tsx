import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { registrationsApi, type Registration, type RegistrationStatus } from '../../api/registrations'
import { usersApi } from '../../api/users'
import { ErrorAlert } from '../../components/ErrorAlert'
import { EmptyState, Loading, StatusBadge, SuccessAlert } from '../../components/Feedback'
import { useLoader } from '../../hooks/useLoader'
import { formatDateTime } from '../../utils/dates'

type Filter = 'ALL' | RegistrationStatus

const FILTERS: { value: Filter; label: string }[] = [
  { value: 'ALL', label: 'All' },
  { value: 'CONFIRMED', label: 'Confirmed' },
  { value: 'WAITING_LIST', label: 'Waitlist' },
  { value: 'CANCELLED', label: 'Cancelled' },
]

interface RegistrationsCardProps {
  eventId: number
  acceptingRegistrations: boolean
  /** Called after a registration or cancellation so the event and availability are refreshed. */
  onChanged: () => void
}

export function RegistrationsCard({ eventId, acceptingRegistrations, onChanged }: RegistrationsCardProps) {
  const { data, error, loading, reload } = useLoader(
    () => Promise.all([registrationsApi.listByEvent(eventId), usersApi.list()]),
    [eventId],
  )
  const [filter, setFilter] = useState<Filter>('ALL')
  const [selectedUserId, setSelectedUserId] = useState('')
  const [actionError, setActionError] = useState<unknown>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [working, setWorking] = useState(false)

  async function run(action: () => Promise<string>) {
    setActionError(null)
    setNotice(null)
    setWorking(true)
    try {
      setNotice(await action())
      reload()
      onChanged()
    } catch (err) {
      setActionError(err)
    } finally {
      setWorking(false)
    }
  }

  function handleRegister(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selectedUserId) return
    void run(async () => {
      const registration = await registrationsApi.register(eventId, Number(selectedUserId))
      setSelectedUserId('')
      return registration.status === 'CONFIRMED'
        ? `${registration.userName} is confirmed`
        : `The event is full: ${registration.userName} was added to the waitlist`
    })
  }

  function handleCancel(registration: Registration) {
    if (!window.confirm(`Cancel the registration of ${registration.userName}?`)) return
    void run(async () => {
      const { promoted } = await registrationsApi.cancel(eventId, registration.userId)
      return promoted
        ? `Registration of ${registration.userName} cancelled. ${promoted.userName} was promoted from the waitlist`
        : `Registration of ${registration.userName} cancelled`
    })
  }

  if (loading && !data) {
    return (
      <div className="card">
        <h2>Registrations</h2>
        <Loading label="Loading registrations…" />
      </div>
    )
  }

  if (error || !data) {
    return (
      <div className="card">
        <h2>Registrations</h2>
        <ErrorAlert error={error} />
      </div>
    )
  }

  const [registrations, users] = data
  const activeUserIds = new Set(registrations.filter((r) => r.status !== 'CANCELLED').map((r) => r.userId))
  const candidates = users.filter((user) => !activeUserIds.has(user.id))
  // The API returns registrations oldest first, which is the order the waitlist is served in.
  const waitlistPositions = new Map(
    registrations.filter((r) => r.status === 'WAITING_LIST').map((r, index) => [r.id, index + 1]),
  )
  const visible = filter === 'ALL' ? registrations : registrations.filter((r) => r.status === filter)
  const countOf = (value: Filter) =>
    value === 'ALL' ? registrations.length : registrations.filter((r) => r.status === value).length

  return (
    <div className="card">
      <h2>Registrations</h2>

      <SuccessAlert message={notice} />
      <ErrorAlert error={actionError} />

      {acceptingRegistrations ? (
        <form className="registration-form" onSubmit={handleRegister}>
          <select
            aria-label="User to register"
            value={selectedUserId}
            onChange={(e) => setSelectedUserId(e.target.value)}
            disabled={working}
          >
            <option value="">Select a user…</option>
            {candidates.map((user) => (
              <option key={user.id} value={user.id}>
                {user.name} ({user.email}){user.status === 'INACTIVE' ? ' — inactive' : ''}
              </option>
            ))}
          </select>
          <button type="submit" disabled={working || !selectedUserId}>
            Register
          </button>
        </form>
      ) : (
        <p className="field-hint">This event is not accepting new registrations.</p>
      )}

      <div className="filter-tabs" role="tablist" aria-label="Filter registrations">
        {FILTERS.map(({ value, label }) => (
          <button
            key={value}
            type="button"
            role="tab"
            aria-selected={filter === value}
            className={filter === value ? 'filter-tab filter-tab-active' : 'filter-tab'}
            onClick={() => setFilter(value)}
          >
            {label} ({countOf(value)})
          </button>
        ))}
      </div>

      {visible.length === 0 ? (
        <EmptyState>
          <p>No registrations{filter === 'ALL' ? ' yet' : ' with this status'}.</p>
        </EmptyState>
      ) : (
        <div className="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>User</th>
                <th>Registered at</th>
                <th>Status</th>
                <th className="numeric">Waitlist</th>
                <th aria-label="Actions" />
              </tr>
            </thead>
            <tbody>
              {visible.map((registration) => (
                <tr key={registration.id}>
                  <td>
                    <Link to={`/users/${registration.userId}`}>{registration.userName}</Link>
                    <div className="field-hint">{registration.userEmail}</div>
                  </td>
                  <td>{formatDateTime(registration.registrationDate)}</td>
                  <td>
                    <StatusBadge status={registration.status} />
                  </td>
                  <td className="numeric">{waitlistPositions.get(registration.id) ?? '—'}</td>
                  <td>
                    {registration.status !== 'CANCELLED' && (
                      <button
                        type="button"
                        className="button-danger"
                        onClick={() => handleCancel(registration)}
                        disabled={working}
                      >
                        Cancel
                      </button>
                    )}
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
