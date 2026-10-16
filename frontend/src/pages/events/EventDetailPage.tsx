import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { eventsApi, NEXT_STATUSES, type EventStatus } from '../../api/events'
import { Breadcrumb } from '../../components/Breadcrumb'
import { Button, ButtonLink, type ButtonVariant } from '../../components/Button'
import { Card } from '../../components/Card'
import { ConfirmPanel } from '../../components/ConfirmPanel'
import { DateTile } from '../../components/DateTile'
import { ErrorAlert } from '../../components/ErrorAlert'
import { Loading, SuccessAlert } from '../../components/Feedback'
import { PlusIcon } from '../../components/icons'
import { StatusBadge } from '../../components/StatusBadge'
import { useLoader } from '../../hooks/useLoader'
import { formatDateTimeRange } from '../../utils/dates'
import { statusLabel } from '../../utils/status'
import { AvailabilityTiles } from './AvailabilityTiles'
import styles from './EventDetailPage.module.css'
import { RegistrationsCard } from './RegistrationsCard'

interface StatusAction {
  label: string
  variant: ButtonVariant
  /** Irreversible changes ask for confirmation first. */
  confirm?: { title: string; text: string; label: string }
}

const STATUS_ACTIONS: Record<EventStatus, StatusAction> = {
  SCHEDULED: { label: 'Schedule', variant: 'secondary' },
  ACTIVE: { label: 'Activate', variant: 'secondary' },
  FINISHED: {
    label: 'Finish',
    variant: 'secondary',
    confirm: {
      title: 'Mark this event as finished?',
      text: 'This cannot be undone: the event will no longer accept registrations or changes.',
      label: 'Finish event',
    },
  },
  CANCELLED: {
    label: 'Cancel event',
    variant: 'danger',
    confirm: {
      title: 'Cancel this event?',
      text: 'This cannot be undone: the event will no longer accept registrations or changes.',
      label: 'Cancel event',
    },
  },
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
  const [pendingStatus, setPendingStatus] = useState<EventStatus | null>(null)
  const [registerOpen, setRegisterOpen] = useState(false)

  async function changeStatus(status: EventStatus) {
    setActionError(null)
    setNotice(null)
    setChanging(true)
    try {
      await eventsApi.updateStatus(eventId, status)
      setNotice(`Status changed to ${statusLabel(status)}`)
      setPendingStatus(null)
      reload()
    } catch (err) {
      setActionError(err)
    } finally {
      setChanging(false)
    }
  }

  function requestStatus(status: EventStatus) {
    if (STATUS_ACTIONS[status].confirm) {
      setPendingStatus(status)
    } else {
      void changeStatus(status)
    }
  }

  if (loading && !data) return <Loading label="Loading event…" />

  if (error || !data) {
    return (
      <section>
        <Breadcrumb items={[{ label: 'Events', to: '/events' }, { label: 'Event' }]} />
        <ErrorAlert error={error} />
      </section>
    )
  }

  const [event, availability] = data
  const nextStatuses = NEXT_STATUSES[event.status]
  const editable = nextStatuses.length > 0
  const pendingConfirm = pendingStatus ? STATUS_ACTIONS[pendingStatus].confirm : undefined

  return (
    <section>
      <Breadcrumb items={[{ label: 'Events', to: '/events' }, { label: event.name }]} />

      <Card className={styles.header}>
        <DateTile date={event.startDate} size="lg" />
        <div className={styles.info}>
          <div className={styles.titleRow}>
            <h1 className={styles.title}>{event.name}</h1>
            <StatusBadge status={event.status} />
          </div>
          <p className={styles.meta}>
            {formatDateTimeRange(event.startDate, event.endDate)} · Capacity {event.capacity}
          </p>
          {event.description && <p className={styles.description}>{event.description}</p>}
          {!editable && (
            <p className={styles.locked}>
              This event is {statusLabel(event.status).toLowerCase()} and can no longer be modified.
            </p>
          )}
        </div>
        <div className={styles.actions}>
          {editable && (
            <ButtonLink to={`/events/${event.id}/edit`} variant="secondary">
              Edit
            </ButtonLink>
          )}
          {nextStatuses.map((status) => (
            <Button
              key={status}
              variant={STATUS_ACTIONS[status].variant}
              onClick={() => requestStatus(status)}
              disabled={changing || pendingStatus !== null}
            >
              {STATUS_ACTIONS[status].label}
            </Button>
          ))}
          <Button
            onClick={() => setRegisterOpen(true)}
            disabled={!availability.acceptingRegistrations || registerOpen}
            title={availability.acceptingRegistrations ? undefined : 'This event is not accepting registrations'}
          >
            <PlusIcon size={18} />
            Register user
          </Button>
        </div>
      </Card>

      {pendingStatus && pendingConfirm && (
        <ConfirmPanel
          title={pendingConfirm.title}
          confirmLabel={pendingConfirm.label}
          onConfirm={() => void changeStatus(pendingStatus)}
          onCancel={() => setPendingStatus(null)}
          busy={changing}
        >
          {pendingConfirm.text}
        </ConfirmPanel>
      )}

      <SuccessAlert message={notice} />
      <ErrorAlert error={actionError} />

      <AvailabilityTiles availability={availability} />

      <RegistrationsCard
        eventId={event.id}
        acceptingRegistrations={availability.acceptingRegistrations}
        isFull={availability.availableSeats === 0}
        registerOpen={registerOpen && availability.acceptingRegistrations}
        onCloseRegister={() => setRegisterOpen(false)}
        onChanged={reload}
      />
    </section>
  )
}
