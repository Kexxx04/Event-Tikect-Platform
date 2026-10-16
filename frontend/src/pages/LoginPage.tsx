import { useState, type FormEvent } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { ErrorAlert } from '../components/ErrorAlert'
import { FormField } from '../components/FormField'

interface LoginForm {
  username: string
  password: string
}

type FieldErrors = Partial<Record<keyof LoginForm, string>>

const FORM_FIELDS = ['username', 'password'] as const

function redirectTarget(state: unknown): string {
  const from = (state as { from?: unknown } | null)?.from
  // Only same-app paths, never an absolute URL.
  return typeof from === 'string' && from.startsWith('/') && !from.startsWith('//') && from !== '/login'
    ? from
    : '/events'
}

export function LoginPage() {
  const { session, notice, login } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const [form, setForm] = useState<LoginForm>({ username: '', password: '' })
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [submitError, setSubmitError] = useState<unknown>(null)
  const [submitting, setSubmitting] = useState(false)

  const target = redirectTarget(location.state)

  if (session) {
    return <Navigate to={target} replace />
  }

  function updateField(field: keyof LoginForm, value: string) {
    setForm((previous) => ({ ...previous, [field]: value }))
    setFieldErrors((previous) => ({ ...previous, [field]: undefined }))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitError(null)
    const errors: FieldErrors = {}
    if (!form.username.trim()) errors.username = 'Username is required'
    if (!form.password) errors.password = 'Password is required'
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return

    setSubmitting(true)
    try {
      await login(form.username.trim(), form.password)
      navigate(target, { replace: true })
    } catch (error) {
      setSubmitError(error)
      if (error instanceof ApiError) setFieldErrors(error.details)
      setForm((previous) => ({ ...previous, password: '' }))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="login-page">
      <form className="card login-card" onSubmit={handleSubmit} noValidate>
        <h1>Event Platform</h1>
        <p className="login-subtitle">Sign in as administrator</p>

        {notice && !submitError && <div className="alert alert-warning">{notice}</div>}
        <ErrorAlert error={submitError} fieldsShownInline={FORM_FIELDS} />

        <FormField label="Username" htmlFor="username" error={fieldErrors.username} required>
          <input
            id="username"
            value={form.username}
            autoComplete="username"
            onChange={(e) => updateField('username', e.target.value)}
            aria-invalid={Boolean(fieldErrors.username)}
            autoFocus
          />
        </FormField>

        <FormField label="Password" htmlFor="password" error={fieldErrors.password} required>
          <input
            id="password"
            type="password"
            value={form.password}
            autoComplete="current-password"
            onChange={(e) => updateField('password', e.target.value)}
            aria-invalid={Boolean(fieldErrors.password)}
          />
        </FormField>

        <button type="submit" className="button-block" disabled={submitting}>
          {submitting ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
    </main>
  )
}
