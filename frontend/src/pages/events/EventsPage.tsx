import { useState } from 'react'
import { Link } from 'react-router-dom'
import { eventsApi, type Event, type EventStatus } from '../../api/events'
import { Button, ButtonLink } from '../../components/Button'
import { DateTile } from '../../components/DateTile'
import { ErrorAlert } from '../../components/ErrorAlert'
import { EmptyState, Loading } from '../../components/Feedback'
import { PlusIcon } from '../../components/icons'
import { PageHeader } from '../../components/PageHeader'
import { PillTabs, type PillOption } from '../../components/PillTabs'
import { ProgressBar, type ProgressTone } from '../../components/ProgressBar'
import { SearchInput } from '../../components/SearchInput'
import { StatusBadge } from '../../components/StatusBadge'
import { useLoader } from '../../hooks/useLoader'
import { formatEventDates } from '../../utils/dates'
import styles from './EventsPage.module.css'

type Filter = 'ALL' | EventStatus

const FILTERS: PillOption<Filter>[] = [
  { value: 'ALL', label: 'All' },
  { value: 'SCHEDULED', label: 'Scheduled' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'FINISHED', label: 'Finished' },
  { value: 'CANCELLED', label: 'Cancelled' },
]

function EventCard({ event }: { event: Event }) {
  const closed = event.status === 'FINISHED' || event.status === 'CANCELLED'
  const full = event.registeredCount >= event.capacity
  const seatsLeft = Math.max(event.capacity - event.registeredCount, 0)
  const tone: ProgressTone = full ? 'full' : closed ? 'closed' : 'accent'

  return (
    <article className={styles.card}>
      <div className={styles.cardTop}>
        <DateTile date={event.startDate} />
        <StatusBadge status={event.status} />
      </div>
      <h2 className={styles.name}>
        <Link to={`/events/${event.id}`} className={styles.link}>
          {event.name}
        </Link>
      </h2>
      <p className={styles.dates}>{formatEventDates(event.startDate, event.endDate)}</p>
      <ProgressBar
        value={event.registeredCount}
        max={event.capacity}
        tone={tone}
        label={`${event.registeredCount} of ${event.capacity} seats taken`}
      />
      <div className={styles.cardFooter}>
        <span>
          {event.registeredCount} / {event.capacity} registered
        </span>
        {closed ? (
          <span className={styles.closed}>Closed</span>
        ) : full ? (
          <span className={styles.full}>Full</span>
        ) : (
          <span className={styles.seatsLeft}>
            {seatsLeft} {seatsLeft === 1 ? 'seat' : 'seats'} left
          </span>
        )}
      </div>
    </article>
  )
}

export function EventsPage() {
  const { data: events, error, loading, reload } = useLoader(() => eventsApi.list(), [])
  const [filter, setFilter] = useState<Filter>('ALL')
  const [query, setQuery] = useState('')

  const search = query.trim().toLowerCase()
  const visible = (events ?? []).filter(
    (event) => (filter === 'ALL' || event.status === filter) && event.name.toLowerCase().includes(search),
  )

  return (
    <section>
      <PageHeader
        title="Events"
        subtitle="Create events and follow their availability in real time."
        actions={
          <ButtonLink to="/events/new">
            <PlusIcon size={18} />
            New event
          </ButtonLink>
        }
      />

      {loading && <Loading label="Loading events…" />}

      {!loading && error !== null && (
        <>
          <ErrorAlert error={error} />
          <Button variant="secondary" onClick={reload}>
            Retry
          </Button>
        </>
      )}

      {!loading && events && events.length === 0 && (
        <EmptyState>
          <p>No events yet.</p>
          <ButtonLink to="/events/new">Create the first event</ButtonLink>
        </EmptyState>
      )}

      {!loading && events && events.length > 0 && (
        <>
          <div className={styles.toolbar}>
            <PillTabs options={FILTERS} value={filter} onChange={setFilter} label="Filter events by status" />
            <SearchInput
              id="event-search"
              label="Search events"
              placeholder="Search events"
              value={query}
              onChange={setQuery}
            />
          </div>

          {visible.length === 0 ? (
            <EmptyState>
              <p>No events match your filters.</p>
            </EmptyState>
          ) : (
            <ul className={styles.grid}>
              {visible.map((event) => (
                <li key={event.id}>
                  <EventCard event={event} />
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </section>
  )
}
