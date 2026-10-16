import type { ButtonHTMLAttributes } from 'react'
import { Link, type LinkProps } from 'react-router-dom'
import styles from './Button.module.css'

export type ButtonVariant = 'primary' | 'secondary' | 'danger'

function buttonClass(variant: ButtonVariant, block: boolean, className?: string): string {
  return [styles.button, styles[variant], block ? styles.block : '', className ?? ''].filter(Boolean).join(' ')
}

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  block?: boolean
  /** Shows a spinner and disables the button while an action is pending. */
  loading?: boolean
}

export function Button({
  variant = 'primary',
  block = false,
  loading = false,
  className,
  disabled,
  type = 'button',
  children,
  ...props
}: ButtonProps) {
  return (
    <button
      type={type}
      className={buttonClass(variant, block, className)}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...props}
    >
      {loading && <span className={styles.spinner} aria-hidden="true" />}
      {children}
    </button>
  )
}

interface ButtonLinkProps extends LinkProps {
  variant?: ButtonVariant
  block?: boolean
}

export function ButtonLink({ variant = 'primary', block = false, className, ...props }: ButtonLinkProps) {
  return <Link className={buttonClass(variant, block, className)} {...props} />
}
