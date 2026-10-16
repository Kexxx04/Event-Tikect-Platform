import { useId, type ReactNode } from 'react'
import { Button } from './Button'
import styles from './ConfirmPanel.module.css'

interface ConfirmPanelProps {
  title: string
  children?: ReactNode
  confirmLabel: string
  onConfirm: () => void
  onCancel: () => void
  busy?: boolean
  tone?: 'danger' | 'primary'
}

/** Inline replacement for window.confirm: the page stays usable and the question is announced. */
export function ConfirmPanel({
  title,
  children,
  confirmLabel,
  onConfirm,
  onCancel,
  busy = false,
  tone = 'danger',
}: ConfirmPanelProps) {
  const titleId = useId()
  return (
    <div className={`${styles.panel} ${styles[tone]}`} role="group" aria-labelledby={titleId}>
      <div className={styles.text}>
        <p id={titleId} className={styles.title}>
          {title}
        </p>
        {children && <p className={styles.description}>{children}</p>}
      </div>
      <div className={styles.actions}>
        <Button variant="secondary" onClick={onCancel} disabled={busy} autoFocus>
          Keep as is
        </Button>
        <Button variant={tone === 'danger' ? 'danger' : 'primary'} onClick={onConfirm} loading={busy}>
          {confirmLabel}
        </Button>
      </div>
    </div>
  )
}
