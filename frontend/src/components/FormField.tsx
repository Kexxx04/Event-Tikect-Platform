import type { ReactNode } from 'react'
import styles from './FormField.module.css'

interface FormFieldProps {
  label: string
  htmlFor: string
  error?: string
  required?: boolean
  hint?: string
  children: ReactNode
}

/** Label + control + hint + error. Spread fieldA11y(...) on the control to link the messages. */
export function FormField({ label, htmlFor, error, required = false, hint, children }: FormFieldProps) {
  return (
    <div className={error ? `${styles.field} ${styles.invalid}` : styles.field}>
      <label htmlFor={htmlFor}>
        {label}
        {required && (
          <span className={styles.required} aria-hidden="true">
            {' '}
            *
          </span>
        )}
      </label>
      {children}
      {hint && !error && (
        <p className={styles.hint} id={`${htmlFor}-hint`}>
          {hint}
        </p>
      )}
      {error && (
        <p className={styles.error} id={`${htmlFor}-error`}>
          {error}
        </p>
      )}
    </div>
  )
}
