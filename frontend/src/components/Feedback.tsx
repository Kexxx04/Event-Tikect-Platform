import type { ReactNode } from 'react'
import { Alert } from './Alert'
import styles from './Feedback.module.css'

export function Spinner() {
  return <span className={styles.spinner} aria-hidden="true" />
}

export function Loading({ label = 'Loading…' }: { label?: string }) {
  return (
    <p className={styles.loading} role="status">
      <Spinner />
      {label}
    </p>
  )
}

export function EmptyState({ children }: { children: ReactNode }) {
  return <div className={styles.empty}>{children}</div>
}

export function SuccessAlert({ message }: { message: string | null }) {
  if (!message) {
    return null
  }
  return <Alert tone="success" title={message} />
}
