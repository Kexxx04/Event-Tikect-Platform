import { useState } from 'react'
import { Link } from 'react-router-dom'
import { registrationsApi, type RegistrationStatus } from '../../api/registrations'
import { Card, CardHeader } from '../../components/Card'
import { DataTable } from '../../components/DataTable'
import { DateTile } from '../../components/DateTile'
import { ErrorAlert } from '../../components/ErrorAlert'
import { EmptyState, Loading } from '../../components/Feedback'
import { PillTabs, type PillOption } from '../../components/PillTabs'
import { StatusBadge } from '../../components/StatusBadge'
import { useLoader } from '../../hooks/useLoader'
import { formatDate } from '../../utils/dates'
import styles from './UserEventsCard.module.css'

type Filter = 'ALL' | RegistrationStatus

const FILTERS: PillOption<Filter>[] = [
  { value: 'ALL', label: 'All' },
  { value: 'CONFIRMED', label: 'Confirmed' },
  { value: 'WAITING_LIST', label: 'Waitlisted' },
  { value: 'CANCELLED', label: 'Cancelled' },
]

export function UserEventsCard({ userId }: { userId: number }) {
  const { data: events, error, loading } = useLoader(() => registrationsApi.eventsOfUser(userId), [userId])
  const [filter, setFilter] = useState<Filter>('ALL')

  const visible = (events ?? []).filter((event) => filter === 'ALL' || event.registrationStatus === filter)
  const hasEvents = !loading && events !== undefined && events.length > 0

  return (
    <Card as="section" aria-labelledby="user-events-title">
      <CardHeader
        title="Events"
        titleId="user-events-title"
        actions={
          hasEvents && <PillTabs options={FILTERS} value={filter} onChange={setFilter} label="Filter by registration" />
        }
      />

      {loading && <Loading label="Loading events…" />}
      {!loading && <ErrorAlert error={error} />}

      {!loading && events && events.length === 0 && (
        <EmptyState>
          <p>This user has no registrations yet.</p>
        </EmptyState>
      )}

      {hasEvents && visible.length === 0 && (
        <EmptyState>
          <p>No registrations with this status.</p>
        </EmptyState>
      )}

      {hasEvents && visible.length > 0 && (
        <DataTable label="Events of this user">
          <thead>
            <tr>
              <th scope="col">Date</th>
              <th scope="col">Event</th>
              <th scope="col">Status</th>
              <th scope="col">Registration</th>
              <th scope="col">Registered on</th>
            </tr>
          </thead>
          <tbody>
            {visible.map((event) => (
              <tr key={event.registrationId}>
                <td className={styles.dateCell}>
                  <DateTile date={event.startDate} size="sm" />
                </td>
                <td>
                  <Link to={`/events/${event.eventId}`}>{event.eventName}</Link>
                </td>
                <td>
                  <StatusBadge status={event.eventStatus} />
                </td>
                <td>
                  <StatusBadge status={event.registrationStatus} />
                </td>
                <td className={styles.muted}>{formatDate(event.registrationDate)}</td>
              </tr>
            ))}
          </tbody>
        </DataTable>
      )}
    </Card>
  )
}
