import { useState, type FormEvent } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { ErrorAlert } from '../components/ErrorAlert'
import { fieldA11y } from '../components/fieldA11y'
import { FormField } from '../components/FormField'
import { CalendarIcon } from '../components/icons'
import { OccupancyRing } from '../components/OccupancyRing'
import styles from './LoginPage.module.css'

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
  const [showPassword, setShowPassword] = useState(false)

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
    <div className={styles.page}>
      <section className={styles.panel} aria-label="Event Platform">
        <div className={styles.brand}>
          <span className={styles.logo}>
            <CalendarIcon size={24} />
          </span>
          Event Platform
        </div>

        <div className={styles.pitch}>
          <p className={styles.headline}>Every seat, accounted for.</p>
          <p className={styles.tagline}>
            Create events, manage registrations and let the waitlist fill freed seats automatically.
          </p>
        </div>

        {/* Decorative preview: no event data is available before signing in. */}
        <div className={styles.preview} aria-hidden="true">
          <OccupancyRing percentage={87} size={56} />
          <div className={styles.previewText}>
            <span className={styles.previewTitle}>Live availability</span>
            <span className={styles.previewLine}>Seats and waitlist, always in sync</span>
          </div>
        </div>
      </section>

      <main className={styles.formSide}>
        <form className={styles.form} onSubmit={handleSubmit} noValidate>
          <h1 className={styles.title}>Welcome back</h1>
          <p className={styles.subtitle}>Sign in to manage your events.</p>

          {notice && !submitError && <Alert tone="warning" title={notice} />}
          <ErrorAlert error={submitError} fieldsShownInline={FORM_FIELDS} />

          <FormField label="Username" htmlFor="username" error={fieldErrors.username} required>
            <input
              id="username"
              value={form.username}
              autoComplete="username"
              placeholder="Your username"
              onChange={(e) => updateField('username', e.target.value)}
              {...fieldA11y('username', fieldErrors.username)}
              autoFocus
            />
          </FormField>

          <FormField label="Password" htmlFor="password" error={fieldErrors.password} required>
            <div className={styles.password}>
              <input
                id="password"
                type={showPassword ? 'text' : 'password'}
                value={form.password}
                autoComplete="current-password"
                onChange={(e) => updateField('password', e.target.value)}
                {...fieldA11y('password', fieldErrors.password)}
              />
              <button
                type="button"
                className={styles.toggle}
                onClick={() => setShowPassword((value) => !value)}
                aria-pressed={showPassword}
                aria-controls="password"
              >
                {showPassword ? 'Hide' : 'Show'}
              </button>
            </div>
          </FormField>

          <Button type="submit" block loading={submitting} className={styles.submit}>
            {submitting ? 'Signing in…' : 'Sign in'}
          </Button>
        </form>
      </main>
    </div>
  )
}
