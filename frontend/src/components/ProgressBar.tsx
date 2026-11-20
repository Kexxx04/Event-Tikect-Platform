import styles from './ProgressBar.module.css'

export type ProgressTone = 'accent' | 'full' | 'closed'

interface ProgressBarProps {
  value: number
  max: number
  tone?: ProgressTone
  label: string
}

/** The fill width is an SVG attribute, so no inline style is needed (CSP friendly). */
export function ProgressBar({ value, max, tone = 'accent', label }: ProgressBarProps) {
  const percentage = max > 0 ? Math.min(100, Math.max(0, (value / max) * 100)) : 0
  return (
    <svg className={`${styles.bar} ${styles[tone]}`} width="100%" height="8" role="img" aria-label={label}>
      <rect className={styles.track} width="100%" height="8" rx="4" />
      {percentage > 0 && (
        <rect className={styles.fill} width={percentage < 2 ? 8 : `${percentage}%`} height="8" rx="4" />
      )}
    </svg>
  )
}
