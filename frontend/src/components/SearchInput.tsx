import { SearchIcon } from './icons'
import styles from './SearchInput.module.css'

interface SearchInputProps {
  id: string
  label: string
  placeholder: string
  value: string
  onChange: (value: string) => void
}

export function SearchInput({ id, label, placeholder, value, onChange }: SearchInputProps) {
  return (
    <div className={styles.search}>
      <label htmlFor={id} className="visually-hidden">
        {label}
      </label>
      <SearchIcon className={styles.icon} size={18} />
      <input
        id={id}
        type="search"
        className={styles.input}
        value={value}
        placeholder={placeholder}
        autoComplete="off"
        onChange={(e) => onChange(e.target.value)}
      />
    </div>
  )
}
