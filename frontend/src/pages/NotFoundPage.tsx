import { useAuth } from '../auth/AuthContext'
import { ButtonLink } from '../components/Button'
import styles from './NotFoundPage.module.css'

/** Rendered outside the app shell, so it works with or without a session. */
export function NotFoundPage() {
  const { session } = useAuth()

  return (
    <main className={styles.page}>
      <section className={styles.card} aria-labelledby="not-found-title">
        <span className={styles.code} aria-hidden="true">
          <span className={styles.codeLabel}>ERR</span>
          <span className={styles.codeNumber}>404</span>
        </span>
        <h1 id="not-found-title" className={styles.title}>
          This page has no seats
        </h1>
        <p className={styles.text}>
          The page you are looking for does not exist or was moved. Check the address or go back to the events.
        </p>
        <div className={styles.actions}>
          <ButtonLink to="/events">Back to events</ButtonLink>
          {!session && (
            <ButtonLink to="/login" variant="secondary">
              Sign in
            </ButtonLink>
          )}
        </div>
      </section>
    </main>
  )
}
