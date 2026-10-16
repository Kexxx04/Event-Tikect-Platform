import type { ReactNode } from 'react'
import styles from './DataTable.module.css'

interface DataTableProps {
  label: string
  /** Keeps the column headers for screen readers but does not draw them. */
  headerHidden?: boolean
  children: ReactNode
}

/**
 * A real <table> drawn as separated rounded rows. Cells with data-align="end" are right-aligned.
 * It scrolls horizontally inside its card on narrow screens.
 */
export function DataTable({ label, headerHidden = false, children }: DataTableProps) {
  return (
    <div className={styles.scroll}>
      <table className={headerHidden ? `${styles.table} ${styles.headerHidden}` : styles.table} aria-label={label}>
        {children}
      </table>
    </div>
  )
}
