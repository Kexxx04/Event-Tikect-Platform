import type { HTMLAttributes, ReactNode } from 'react'
import styles from './Card.module.css'

interface CardProps extends HTMLAttributes<HTMLElement> {
  as?: 'div' | 'section' | 'aside'
}

export function Card({ as: Tag = 'div', className, children, ...props }: CardProps) {
  return (
    <Tag className={className ? `${styles.card} ${className}` : styles.card} {...props}>
      {children}
    </Tag>
  )
}

interface CardHeaderProps {
  title: ReactNode
  titleId?: string
  actions?: ReactNode
}

export function CardHeader({ title, titleId, actions }: CardHeaderProps) {
  return (
    <div className={styles.header}>
      <h2 id={titleId} className={styles.title}>
        {title}
      </h2>
      {actions && <div className={styles.actions}>{actions}</div>}
    </div>
  )
}
