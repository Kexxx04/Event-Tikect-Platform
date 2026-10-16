import { statusLabel, statusTone, type AnyStatus } from '../utils/status'
import styles from './StatusBadge.module.css'

export function StatusBadge({ status }: { status: AnyStatus }) {
  return <span className={`${styles.badge} ${styles[statusTone(status)]}`}>{statusLabel(status)}</span>
}
