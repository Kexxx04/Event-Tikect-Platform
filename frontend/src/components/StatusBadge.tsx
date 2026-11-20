import { statusLabel, statusTone, type AnyStatus } from '../utils/status'
import styles from './StatusBadge.module.css'

interface StatusBadgeProps {
  status: AnyStatus
  /** Stable hook for the end-to-end tests (data-testid). */
  testId?: string
}

export function StatusBadge({ status, testId }: StatusBadgeProps) {
  return (
    <span className={`${styles.badge} ${styles[statusTone(status)]}`} data-testid={testId}>
      {statusLabel(status)}
    </span>
  )
}
