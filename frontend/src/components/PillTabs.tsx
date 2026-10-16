import styles from './PillTabs.module.css'

export interface PillOption<T extends string> {
  value: T
  label: string
  count?: number
}

interface PillTabsProps<T extends string> {
  options: readonly PillOption<T>[]
  value: T
  onChange: (value: T) => void
  label: string
}

export function PillTabs<T extends string>({ options, value, onChange, label }: PillTabsProps<T>) {
  return (
    <div className={styles.pills} role="group" aria-label={label}>
      {options.map((option) => {
        const active = option.value === value
        return (
          <button
            key={option.value}
            type="button"
            className={active ? `${styles.pill} ${styles.active}` : styles.pill}
            aria-pressed={active}
            onClick={() => onChange(option.value)}
          >
            {option.label}
            {option.count !== undefined && ` · ${option.count}`}
          </button>
        )
      })}
    </div>
  )
}
