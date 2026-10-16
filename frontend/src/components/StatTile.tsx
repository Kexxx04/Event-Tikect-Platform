import type { ReactNode } from 'react'
import { Card } from './Card'
import styles from './StatTile.module.css'

export type StatTone = 'default' | 'accent' | 'active' | 'waitlist' | 'muted'

interface StatTileProps {
  label: string
  value: ReactNode
  caption?: ReactNode
  valueTone?: StatTone
  captionTone?: StatTone
  /** Optional graphic shown left of the text, e.g. an OccupancyRing. */
  visual?: ReactNode
}

export function StatTile({
  label,
  value,
  caption,
  valueTone = 'default',
  captionTone = 'muted',
  visual,
}: StatTileProps) {
  return (
    <Card className={styles.tile}>
      {visual}
      <div className={styles.text}>
        <p className={styles.label}>{label}</p>
        <p className={`${styles.value} ${styles[valueTone]}`}>{value}</p>
        {caption && <p className={`${styles.caption} ${styles[captionTone]}`}>{caption}</p>}
      </div>
    </Card>
  )
}
