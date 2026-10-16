import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { registrationsApi, type Registration, type RegistrationStatus } from '../../api/registrations'
import { usersApi } from '../../api/users'
import { Alert } from '../../components/Alert'
import { Avatar } from '../../components/Avatar'
import { Button } from '../../components/Button'
import { Card, CardHeader } from '../../components/Card'
import { ConfirmPanel } from '../../components/ConfirmPanel'
import { DataTable } from '../../components/DataTable'
import { ErrorAlert } from '../../components/ErrorAlert'
import { EmptyState, Loading, SuccessAlert } from '../../components/Feedback'
import { FormField } from '../../components/FormField'
import { PillTabs, type PillOption } from '../../components/PillTabs'
import { StatusBadge } from '../../components/StatusBadge'
import { useLoader } from '../../hooks/useLoader'
import { formatDate } from '../../utils/dates'
import styles from './RegistrationsCard.module.css'

const FILTERS: { value: RegistrationStatus; label: string; empty: string }[] = [
  { value: 'CONFIRMED', label: 'Confirmed', empty: 'No confirmed registrations yet.' },
  { value: 'WAITING_LIST', label: 'Waitlist', empty: 'Nobody is on the waitlist.' },
  { value: 'CANCELLED', label: 'Cancelled', empty: 'No cancelled registrations.' },
]

interface RegistrationsCardProps {
  eventId: number
  acceptingRegistrations: boolean
  isFull: boolean
  /** The register panel is opened from the "Register user" button of the event header. */
  registerOpen: boolean
  onCloseRegister: () => void
  /** Called after a registration or cancellation so the event and availability are refreshed. */
  onChanged: () => void
}

export function RegistrationsCard({
  eventId,
  acceptingRegistrations,
  isFull,
  registerOpen,
  onCloseRegister,
  onChanged,
}: RegistrationsCardProps) {
  const { data, error, loading, reload } = useLoader(
    () => Promise.all([registrationsApi.listByEvent(eventId), usersApi.list()]),
    [eventId],
  )
  const [filter, setFilter] = useState<RegistrationStatus>('CONFIRMED')
  const [selectedUserId, setSelectedUserId] = useState('')
  const [pendingCancel, setPendingCancel] = useState<Registration | null>(null)
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
      setFilter(registration.status)
      onCloseRegister()
      return registration.status === 'CONFIRMED'
        ? `${registration.userName} is confirmed`
        : `The event is full: ${registration.userName} was added to the waitlist`
    })
  }

  function confirmCancel(registration: Registration) {
    void run(async () => {
      const { promoted } = await registrationsApi.cancel(eventId, registration.userId)
      setPendingCancel(null)
      return promoted
        ? `Registration of ${registration.userName} cancelled. ${promoted.userName} was promoted from the waitlist`
        : `Registration of ${registration.userName} cancelled`
    })
  }

  if (loading && !data) {
    return (
      <Card as="section">
        <CardHeader title="Registrations" />
        <Loading label="Loading registrations…" />
      </Card>
    )
  }

  if (error || !data) {
    return (
      <Card as="section">
        <CardHeader title="Registrations" />
        <ErrorAlert error={error} />
      </Card>
    )
  }

  const [registrations, users] = data
  const activeUserIds = new Set(registrations.filter((r) => r.status !== 'CANCELLED').map((r) => r.userId))
  const candidates = users.filter((user) => !activeUserIds.has(user.id))
  // The API returns registrations oldest first, which is the order the waitlist is served in.
  const waitlistPositions = new Map(
    registrations.filter((r) => r.status === 'WAITING_LIST').map((r, index) => [r.id, index + 1]),
  )
  const visible = registrations.filter((r) => r.status === filter)
  const pills: PillOption<RegistrationStatus>[] = FILTERS.map(({ value, label }) => ({
    value,
    label,
    count: registrations.filter((r) => r.status === value).length,
  }))
  const emptyText = FILTERS.find((option) => option.value === filter)?.empty

  return (
    <Card as="section" aria-labelledby="registrations-title">
      <CardHeader
        title="Registrations"
        titleId="registrations-title"
        actions={<PillTabs options={pills} value={filter} onChange={setFilter} label="Filter registrations" />}
      />

      <SuccessAlert message={notice} />
      <ErrorAlert error={actionError} />

      {registerOpen && acceptingRegistrations && (
        <form className={styles.registerPanel} onSubmit={handleRegister}>
          <FormField label="User to register" htmlFor="register-user">
            <select
              id="register-user"
              value={selectedUserId}
              onChange={(e) => setSelectedUserId(e.target.value)}
              disabled={working}
              autoFocus
            >
              <option value="">Select a user…</option>
              {candidates.map((user) => (
                <option key={user.id} value={user.id}>
                  {user.name} ({user.email}){user.status === 'INACTIVE' ? ' — inactive' : ''}
                </option>
              ))}
            </select>
          </FormField>
          {isFull && <Alert tone="warning" role="note" title="The event is full: the user will join the waitlist." />}
          <div className={styles.registerActions}>
            <Button variant="secondary" onClick={onCloseRegister} disabled={working}>
              Close
            </Button>
            <Button type="submit" disabled={!selectedUserId} loading={working}>
              Register
            </Button>
          </div>
        </form>
      )}

      {pendingCancel && (
        <ConfirmPanel
          title={`Cancel the registration of ${pendingCancel.userName}?`}
          confirmLabel="Cancel registration"
          onConfirm={() => confirmCancel(pendingCancel)}
          onCancel={() => setPendingCancel(null)}
          busy={working}
        >
          {pendingCancel.status === 'CONFIRMED'
            ? 'Their seat is released and the oldest waitlisted registration is promoted automatically.'
            : 'They leave the waitlist.'}
        </ConfirmPanel>
      )}

      {visible.length === 0 ? (
        <EmptyState>
          <p>{emptyText}</p>
        </EmptyState>
      ) : (
        <DataTable label="Registrations" headerHidden>
          <thead>
            <tr>
              <th scope="col">User</th>
              <th scope="col">Email</th>
              <th scope="col">Status</th>
              <th scope="col">Registered on</th>
              <th scope="col">Actions</th>
            </tr>
          </thead>
          <tbody>
            {visible.map((registration) => {
              const position = waitlistPositions.get(registration.id)
              return (
                <tr key={registration.id}>
                  <td>
                    <span className={styles.person}>
                      <Avatar name={registration.userName} />
                      <Link to={`/users/${registration.userId}`}>{registration.userName}</Link>
                    </span>
                  </td>
                  <td className={styles.muted}>{registration.userEmail}</td>
                  <td>
                    <span className={styles.status}>
                      <StatusBadge status={registration.status} />
                      {position !== undefined && <span className={styles.position}>#{position} in line</span>}
                    </span>
                  </td>
                  <td className={styles.muted}>{formatDate(registration.registrationDate)}</td>
                  <td data-align="end">
                    {registration.status !== 'CANCELLED' && (
                      <Button
                        variant="secondary"
                        onClick={() => setPendingCancel(registration)}
                        disabled={working || pendingCancel !== null}
                        aria-label={`Cancel the registration of ${registration.userName}`}
                      >
                        Cancel
                      </Button>
                    )}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </DataTable>
      )}
    </Card>
  )
}
