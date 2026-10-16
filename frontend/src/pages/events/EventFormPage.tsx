import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { eventsApi, type Event, type EventRequest } from '../../api/events'
import { ErrorAlert } from '../../components/ErrorAlert'
import { Loading } from '../../components/Feedback'
import { FormField } from '../../components/FormField'
import { toApiDateTime, toInputDateTime } from '../../utils/dates'

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
    errors.endDate = 'End date must be after start date'
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

  if (loading) return <Loading label="Loading event…" />

  if (loadError) {
    return (
      <section>
        <Link to="/events" className="back-link">
          ← Back to events
        </Link>
        <ErrorAlert error={loadError} />
      </section>
    )
  }

  return (
    <section>
      <Link to={backLink} className="back-link">
        ← Back
      </Link>
      <div className="page-header">
        <h1>{isEdit ? `Edit event: ${event?.name ?? ''}` : 'New event'}</h1>
      </div>

      {isClosed && (
        <div className="alert alert-warning">
          This event is {event?.status}. The API will reject any change to it.
        </div>
      )}

      <form className="card form" onSubmit={handleSubmit} noValidate>
        <ErrorAlert error={submitError} fieldsShownInline={FORM_FIELDS} />

        <FormField label="Name" htmlFor="name" error={fieldErrors.name} required>
          <input
            id="name"
            value={form.name}
            maxLength={255}
            onChange={(e) => updateField('name', e.target.value)}
            aria-invalid={Boolean(fieldErrors.name)}
            autoFocus
          />
        </FormField>

        <FormField label="Description" htmlFor="description" error={fieldErrors.description}>
          <textarea
            id="description"
            value={form.description}
            maxLength={1000}
            onChange={(e) => updateField('description', e.target.value)}
            aria-invalid={Boolean(fieldErrors.description)}
          />
        </FormField>

        <div className="form-row">
          <FormField label="Start date" htmlFor="startDate" error={fieldErrors.startDate} required>
            <input
              id="startDate"
              type="datetime-local"
              value={form.startDate}
              onChange={(e) => updateField('startDate', e.target.value)}
              aria-invalid={Boolean(fieldErrors.startDate)}
            />
          </FormField>

          <FormField label="End date" htmlFor="endDate" error={fieldErrors.endDate} required>
            <input
              id="endDate"
              type="datetime-local"
              value={form.endDate}
              min={form.startDate || undefined}
              onChange={(e) => updateField('endDate', e.target.value)}
              aria-invalid={Boolean(fieldErrors.endDate)}
            />
          </FormField>
        </div>

        <FormField
          label="Capacity"
          htmlFor="capacity"
          error={fieldErrors.capacity}
          required
          hint={event ? `Currently ${event.registeredCount} registered; capacity cannot go below that.` : undefined}
        >
          <input
            id="capacity"
            type="number"
            min={1}
            step={1}
            inputMode="numeric"
            value={form.capacity}
            onChange={(e) => updateField('capacity', e.target.value)}
            aria-invalid={Boolean(fieldErrors.capacity)}
          />
        </FormField>

        <div className="actions">
          <button type="submit" disabled={saving}>
            {saving ? 'Saving…' : isEdit ? 'Save changes' : 'Create event'}
          </button>
          <Link to={backLink} className="button button-secondary">
            Cancel
          </Link>
        </div>
      </form>
    </section>
  )
}
