import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { usersApi, type User } from '../../api/users'
import { Breadcrumb, type Crumb } from '../../components/Breadcrumb'
import { Button, ButtonLink } from '../../components/Button'
import { Card } from '../../components/Card'
import { ErrorAlert } from '../../components/ErrorAlert'
import { Loading } from '../../components/Feedback'
import { fieldA11y } from '../../components/fieldA11y'
import { FormField } from '../../components/FormField'
import { PageHeader } from '../../components/PageHeader'
import styles from './UserFormPage.module.css'

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

/** Create (/users/new) and edit (/users/:id/edit). Status and registrations live on the detail page. */
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

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitError(null)
    const errors = validate(form)
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return

    const payload = { name: form.name.trim(), email: form.email.trim(), document: form.document.trim() }
    setSaving(true)
    try {
      if (isEdit) {
        await usersApi.update(userId, payload)
        navigate(`/users/${userId}`, { state: { notice: 'User saved' } })
      } else {
        await usersApi.create(payload)
        navigate('/users')
      }
    } catch (error) {
      setSubmitError(error)
      if (error instanceof ApiError) {
        setFieldErrors(error.details)
      }
    } finally {
      setSaving(false)
    }
  }

  const backLink = isEdit ? `/users/${userId}` : '/users'
  const crumbs: Crumb[] = isEdit
    ? [{ label: 'Users', to: '/users' }, { label: user?.name ?? 'User', to: backLink }, { label: 'Edit' }]
    : [{ label: 'Users', to: '/users' }, { label: 'New user' }]

  if (loading) return <Loading label="Loading user…" />

  if (loadError) {
    return (
      <section>
        <Breadcrumb items={[{ label: 'Users', to: '/users' }, { label: 'Edit' }]} />
        <ErrorAlert error={loadError} />
      </section>
    )
  }

  return (
    <section>
      <Breadcrumb items={crumbs} />
      <PageHeader
        title={isEdit ? 'Edit user' : 'New user'}
        subtitle={isEdit ? user?.name : 'New users are created as Active.'}
      />

      <Card className={styles.card}>
        <form onSubmit={handleSubmit} noValidate>
          <ErrorAlert error={submitError} fieldsShownInline={FORM_FIELDS} />

          <FormField label="Full name" htmlFor="name" error={fieldErrors.name} required>
            <input
              id="name"
              value={form.name}
              maxLength={255}
              autoComplete="off"
              onChange={(e) => updateField('name', e.target.value)}
              {...fieldA11y('name', fieldErrors.name)}
              autoFocus
            />
          </FormField>

          <FormField label="Email" htmlFor="email" error={fieldErrors.email} required>
            <input
              id="email"
              type="email"
              value={form.email}
              maxLength={255}
              autoComplete="off"
              onChange={(e) => updateField('email', e.target.value)}
              {...fieldA11y('email', fieldErrors.email)}
            />
          </FormField>

          <FormField label="Document number" htmlFor="document" error={fieldErrors.document} required>
            <input
              id="document"
              value={form.document}
              maxLength={255}
              autoComplete="off"
              onChange={(e) => updateField('document', e.target.value)}
              {...fieldA11y('document', fieldErrors.document)}
            />
          </FormField>

          <div className={styles.footer}>
            <ButtonLink to={backLink} variant="secondary">
              Cancel
            </ButtonLink>
            <Button type="submit" loading={saving}>
              {isEdit ? 'Save changes' : 'Create user'}
            </Button>
          </div>
        </form>
      </Card>
    </section>
  )
}
