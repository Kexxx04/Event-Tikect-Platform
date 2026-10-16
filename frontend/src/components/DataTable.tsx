import type { ReactNode } from 'react'
import styles from './DataTable.module.css'

/**
 * A real <table> drawn as separated rounded rows. Cells with data-align="end" are right-aligned.
 * It scrolls horizontally inside its card on narrow screens.
 */
export function DataTable({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className={styles.scroll}>
      <table className={styles.table} aria-label={label}>
        {children}
      </table>
    </div>
  )
}
