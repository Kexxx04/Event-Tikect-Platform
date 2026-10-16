import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { eventsApi, type Event, type EventRequest } from '../../api/events'
import { Alert } from '../../components/Alert'
import { Breadcrumb, type Crumb } from '../../components/Breadcrumb'
import { Button, ButtonLink } from '../../components/Button'
import { Card, CardHeader } from '../../components/Card'
import { ErrorAlert } from '../../components/ErrorAlert'
import { Loading } from '../../components/Feedback'
import { fieldA11y } from '../../components/fieldA11y'
import { FormField } from '../../components/FormField'
import { CheckIcon } from '../../components/icons'
import { PageHeader } from '../../components/PageHeader'
import { toApiDateTime, toInputDateTime } from '../../utils/dates'
import { statusLabel } from '../../utils/status'
import styles from './EventFormPage.module.css'

interface EventForm {
  name: string
  description: string
  startDate: string
  endDate: string
  capacity: string
}

type FieldErrors = Partial<Record<keyof EventForm, string>>

const EMPTY_FORM: EventForm = { name: '', description: '', startDate: '', endDate: '', capacity: '' }
const FORM_FIELDS = ['name', 'description', 'startDate', 'endDate', 'capacity'] as const

// The same rules the backend enforces (EventService), shown here as plain language.
const GOOD_TO_KNOW = [
  'Registrations close automatically when the event starts.',
  'When full, new registrations go to the waitlist.',
  'Raising capacity later promotes people from the waitlist.',
]

function toForm(event: Event): EventForm {
  return {
    name: event.name,
    description: event.description ?? '',
    startDate: toInputDateTime(event.startDate),
    endDate: toInputDateTime(event.endDate),
    capacity: String(event.capacity),
  }
}

function validate(form: EventForm): FieldErrors {
  const errors: FieldErrors = {}
  if (!form.name.trim()) errors.name = 'Name is required'
  if (!form.startDate) errors.startDate = 'Start date is required'
  if (!form.endDate) errors.endDate = 'End date is required'
  else if (form.startDate && form.endDate <= form.startDate) {
    // Both values share the "yyyy-MM-ddTHH:mm" format, so string order is chronological order.
    errors.endDate = 'End date must be after the start date.'
  }
  const capacity = Number(form.capacity)
  if (!form.capacity.trim()) errors.capacity = 'Capacity is required'
  else if (!Number.isInteger(capacity) || capacity <= 0) errors.capacity = 'Capacity must be a whole number greater than zero'
  return errors
}

function toRequest(form: EventForm): EventRequest {
  return {
    name: form.name.trim(),
    description: form.description.trim() || null,
    startDate: toApiDateTime(form.startDate),
    endDate: toApiDateTime(form.endDate),
    capacity: Number(form.capacity),
  }
}

export function EventFormPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const isEdit = id !== undefined
  const eventId = Number(id)

  const [event, setEvent] = useState<Event | null>(null)
  const [form, setForm] = useState<EventForm>(EMPTY_FORM)
  const [loading, setLoading] = useState(isEdit)
  const [loadError, setLoadError] = useState<unknown>(null)
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [submitError, setSubmitError] = useState<unknown>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!isEdit) return
    let active = true
    eventsApi
      .get(eventId)
      .then((loaded) => {
        if (!active) return
        setEvent(loaded)
        setForm(toForm(loaded))
      })
      .catch((error: unknown) => {
        if (active) setLoadError(error)
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [isEdit, eventId])

  function updateField(field: keyof EventForm, value: string) {
    setForm((previous) => ({ ...previous, [field]: value }))
    setFieldErrors((previous) => ({ ...previous, [field]: undefined }))
  }

  async function handleSubmit(submitEvent: FormEvent<HTMLFormElement>) {
    submitEvent.preventDefault()
    setSubmitError(null)
    const errors = validate(form)
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return

    setSaving(true)
    try {
      const saved = isEdit
        ? await eventsApi.update(eventId, toRequest(form))
        : await eventsApi.create(toRequest(form))
      navigate(`/events/${saved.id}`)
    } catch (error) {
      setSubmitError(error)
      if (error instanceof ApiError) {
        setFieldErrors(error.details)
      }
    } finally {
      setSaving(false)
    }
  }

  const backLink = isEdit ? `/events/${eventId}` : '/events'
  const isClosed = event?.status === 'FINISHED' || event?.status === 'CANCELLED'
  const crumbs: Crumb[] = isEdit
    ? [{ label: 'Events', to: '/events' }, { label: event?.name ?? 'Event', to: backLink }, { label: 'Edit' }]
    : [{ label: 'Events', to: '/events' }, { label: 'New event' }]
  const capacityHint = event
    ? `Currently ${event.registeredCount} registered; capacity cannot go below that.`
    : 'Must be greater than 0.'

  if (loading) return <Loading label="Loading event…" />

  if (loadError) {
    return (
      <section>
        <Breadcrumb items={[{ label: 'Events', to: '/events' }, { label: 'Edit' }]} />
        <ErrorAlert error={loadError} />
      </section>
    )
  }

  return (
    <section>
      <Breadcrumb items={crumbs} />
      <PageHeader
        title={isEdit ? 'Edit event' : 'New event'}
        subtitle={isEdit ? event?.name : 'The event starts as Scheduled with 0 registrations.'}
      />

      {isClosed && event && (
        <Alert
          tone="warning"
          title={`This event is ${statusLabel(event.status).toLowerCase()}. The API will reject any change to it.`}
        />
      )}

      <div className={styles.layout}>
        <Card>
          <form onSubmit={handleSubmit} noValidate>
            <ErrorAlert error={submitError} fieldsShownInline={FORM_FIELDS} />

            <FormField label="Name" htmlFor="name" error={fieldErrors.name} required>
              <input
                id="name"
                value={form.name}
                maxLength={255}
                onChange={(e) => updateField('name', e.target.value)}
                {...fieldA11y('name', fieldErrors.name)}
                autoFocus
              />
            </FormField>

            <FormField label="Description" htmlFor="description" error={fieldErrors.description}>
              <textarea
                id="description"
                value={form.description}
                maxLength={1000}
                onChange={(e) => updateField('description', e.target.value)}
                {...fieldA11y('description', fieldErrors.description)}
              />
            </FormField>

            <div className={styles.dates}>
              <FormField label="Start date" htmlFor="startDate" error={fieldErrors.startDate} required>
                <input
                  id="startDate"
                  type="datetime-local"
                  value={form.startDate}
                  onChange={(e) => updateField('startDate', e.target.value)}
                  {...fieldA11y('startDate', fieldErrors.startDate)}
                />
              </FormField>

              <FormField label="End date" htmlFor="endDate" error={fieldErrors.endDate} required>
                <input
                  id="endDate"
                  type="datetime-local"
                  value={form.endDate}
                  min={form.startDate || undefined}
                  onChange={(e) => updateField('endDate', e.target.value)}
                  {...fieldA11y('endDate', fieldErrors.endDate)}
                />
              </FormField>
            </div>

            <div className={styles.capacity}>
              <FormField label="Capacity" htmlFor="capacity" error={fieldErrors.capacity} required hint={capacityHint}>
                <input
                  id="capacity"
                  type="number"
                  min={1}
                  step={1}
                  inputMode="numeric"
                  value={form.capacity}
                  onChange={(e) => updateField('capacity', e.target.value)}
                  {...fieldA11y('capacity', fieldErrors.capacity, capacityHint)}
                />
              </FormField>
            </div>

            <div className={styles.footer}>
              <ButtonLink to={backLink} variant="secondary">
                Cancel
              </ButtonLink>
              <Button type="submit" loading={saving}>
                {isEdit ? 'Save changes' : 'Create event'}
              </Button>
            </div>
          </form>
        </Card>

        <div className={styles.aside}>
          <Card as="aside" aria-labelledby="good-to-know">
            <CardHeader title="Good to know" titleId="good-to-know" />
            <ul className={styles.checklist}>
              {GOOD_TO_KNOW.map((tip) => (
                <li key={tip}>
                  <CheckIcon size={18} className={styles.check} />
                  {tip}
                </li>
              ))}
            </ul>
          </Card>

          {isEdit && (
            <Alert tone="warning" role="note" icon={false}>
              <p>
                <strong>Editing:</strong> capacity cannot be lower than current registrations, and finished or
                cancelled events cannot be edited.
              </p>
            </Alert>
          )}
        </div>
      </div>
    </section>
  )
}
