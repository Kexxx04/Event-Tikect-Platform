import { Link } from 'react-router-dom'
import styles from './Breadcrumb.module.css'

export interface Crumb {
  label: string
  to?: string
}

export function Breadcrumb({ items }: { items: readonly Crumb[] }) {
  return (
    <nav aria-label="Breadcrumb" className={styles.breadcrumb}>
      <ol>
        {items.map((item, index) => (
          <li key={`${index}-${item.label}`}>
            {item.to ? <Link to={item.to}>{item.label}</Link> : <span aria-current="page">{item.label}</span>}
          </li>
        ))}
      </ol>
    </nav>
  )
}
