import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { usersApi, type User } from '../../api/users'
import { ErrorAlert } from '../../components/ErrorAlert'
import { Loading, StatusBadge, SuccessAlert } from '../../components/Feedback'
import { FormField } from '../../components/FormField'

interface UserForm {
  name: string
  email: string
  document: string
}

type FieldErrors = Partial<Record<keyof UserForm, string>>

const EMPTY_FORM: UserForm = { name: '', email: '', document: '' }
const FORM_FIELDS = ['name', 'email', 'document'] as const
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function validate(form: UserForm): FieldErrors {
  const errors: FieldErrors = {}
  if (!form.name.trim()) errors.name = 'Name is required'
  if (!form.email.trim()) errors.email = 'Email is required'
  else if (!EMAIL_PATTERN.test(form.email.trim())) errors.email = 'Email must be valid'
  if (!form.document.trim()) errors.document = 'Document is required'
  return errors
}

export function UserFormPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const isEdit = id !== undefined
  const userId = Number(id)

  const [user, setUser] = useState<User | null>(null)
  const [form, setForm] = useState<UserForm>(EMPTY_FORM)
  const [loading, setLoading] = useState(isEdit)
  const [loadError, setLoadError] = useState<unknown>(null)
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [submitError, setSubmitError] = useState<unknown>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!isEdit) return
    let active = true
    usersApi
      .get(userId)
      .then((loaded) => {
        if (!active) return
        setUser(loaded)
        setForm({ name: loaded.name, email: loaded.email, document: loaded.document })
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
  }, [isEdit, userId])

  function updateField(field: keyof UserForm, value: string) {
    setForm((previous) => ({ ...previous, [field]: value }))
    setFieldErrors((previous) => ({ ...previous, [field]: undefined }))
  }

  function handleError(error: unknown) {
    setSubmitError(error)
    if (error instanceof ApiError) {
      setFieldErrors(error.details)
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setNotice(null)
    setSubmitError(null)
    const errors = validate(form)
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return

    const payload = { name: form.name.trim(), email: form.email.trim(), document: form.document.trim() }
    setSaving(true)
    try {
      if (isEdit) {
        const updated = await usersApi.update(userId, payload)
        setUser(updated)
        setForm({ name: updated.name, email: updated.email, document: updated.document })
        setNotice('User saved')
      } else {
        await usersApi.create(payload)
        navigate('/users')
      }
    } catch (error) {
      handleError(error)
    } finally {
      setSaving(false)
    }
  }

  async function toggleStatus() {
    if (!user) return
    setNotice(null)
    setSubmitError(null)
    const nextStatus = user.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
    setSaving(true)
    try {
      const updated = await usersApi.updateStatus(user.id, nextStatus)
      setUser(updated)
      setNotice(nextStatus === 'ACTIVE' ? 'User activated' : 'User deactivated')
    } catch (error) {
      handleError(error)
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <Loading label="Loading user…" />

  if (loadError) {
    return (
      <section>
        <Link to="/users" className="back-link">
          ← Back to users
        </Link>
        <ErrorAlert error={loadError} />
      </section>
    )
  }

  return (
    <section>
      <Link to="/users" className="back-link">
        ← Back to users
      </Link>
      <div className="page-header">
        <h1>{isEdit ? `Edit user #${userId}` : 'New user'}</h1>
        {user && (
          <div className="actions">
            <StatusBadge status={user.status} />
            <button
              type="button"
              className={user.status === 'ACTIVE' ? 'button-danger' : 'button-secondary'}
              onClick={toggleStatus}
              disabled={saving}
            >
              {user.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
            </button>
          </div>
        )}
      </div>

      <form className="card form" onSubmit={handleSubmit} noValidate>
        <SuccessAlert message={notice} />
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

        <FormField label="Email" htmlFor="email" error={fieldErrors.email} required>
          <input
            id="email"
            type="email"
            value={form.email}
            maxLength={255}
            onChange={(e) => updateField('email', e.target.value)}
            aria-invalid={Boolean(fieldErrors.email)}
          />
        </FormField>

        <FormField label="Document" htmlFor="document" error={fieldErrors.document} required>
          <input
            id="document"
            value={form.document}
            maxLength={255}
            onChange={(e) => updateField('document', e.target.value)}
            aria-invalid={Boolean(fieldErrors.document)}
          />
        </FormField>

        <div className="actions">
          <button type="submit" disabled={saving}>
            {saving ? 'Saving…' : isEdit ? 'Save changes' : 'Create user'}
          </button>
          <Link to="/users" className="button button-secondary">
            Cancel
          </Link>
        </div>
      </form>
    </section>
  )
}
