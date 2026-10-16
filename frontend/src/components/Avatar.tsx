import { initials } from '../utils/initials'
import styles from './Avatar.module.css'

/** Decorative: the name is always rendered next to it. */
export function Avatar({ name, size = 'sm' }: { name: string; size?: 'sm' | 'md' | 'lg' }) {
  return (
    <span className={`${styles.avatar} ${styles[size]}`} aria-hidden="true">
      {initials(name)}
    </span>
  )
}
