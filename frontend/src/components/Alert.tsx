import type { AriaRole, ReactNode } from 'react'
import { AlertCircleIcon, AlertTriangleIcon, CheckCircleIcon } from './icons'
import styles from './Alert.module.css'

export type AlertTone = 'error' | 'warning' | 'success'

interface AlertProps {
  tone: AlertTone
  title?: ReactNode
  children?: ReactNode
  /** Defaults to "alert" for errors and warnings and "status" for success; use "note" for static callouts. */
  role?: AriaRole
  icon?: boolean
}

const ICONS = { error: AlertCircleIcon, warning: AlertTriangleIcon, success: CheckCircleIcon }

export function Alert({ tone, title, children, role, icon = true }: AlertProps) {
  const Icon = ICONS[tone]
  return (
    <div className={`${styles.alert} ${styles[tone]}`} role={role ?? (tone === 'success' ? 'status' : 'alert')}>
      {icon && <Icon className={styles.icon} size={20} />}
      <div className={styles.body}>
        {title && <p className={styles.title}>{title}</p>}
        {children}
      </div>
    </div>
  )
}
