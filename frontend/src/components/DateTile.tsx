import { dateTileParts, formatDate } from '../utils/dates'
import styles from './DateTile.module.css'

export function DateTile({ date, size = 'md' }: { date: string; size?: 'sm' | 'md' | 'lg' }) {
  const { month, day } = dateTileParts(date)
  return (
    <span className={`${styles.tile} ${styles[size]}`}>
      <span className={styles.month} aria-hidden="true">
        {month}
      </span>
      <span className={styles.day} aria-hidden="true">
        {day}
      </span>
      <span className="visually-hidden">{formatDate(date)}</span>
    </span>
  )
}
