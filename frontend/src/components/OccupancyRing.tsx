import styles from './OccupancyRing.module.css'

interface OccupancyRingProps {
  /** 0 to 100 */
  percentage: number
  size?: number
  full?: boolean
}

/** Donut drawn with stroke-dasharray (an SVG attribute), so it needs no inline style. */
export function OccupancyRing({ percentage, size = 72, full = false }: OccupancyRingProps) {
  const strokeWidth = size / 9
  const radius = (size - strokeWidth) / 2
  const circumference = 2 * Math.PI * radius
  const value = Math.min(100, Math.max(0, percentage))
  const center = size / 2

  return (
    <svg
      className={full ? `${styles.ring} ${styles.full}` : styles.ring}
      width={size}
      height={size}
      viewBox={`0 0 ${size} ${size}`}
      role="img"
      aria-label={`Occupancy ${Math.round(value)}%`}
    >
      <circle className={styles.track} cx={center} cy={center} r={radius} strokeWidth={strokeWidth} fill="none" />
      {value > 0 && (
        <circle
          className={styles.value}
          cx={center}
          cy={center}
          r={radius}
          strokeWidth={strokeWidth}
          fill="none"
          strokeLinecap="round"
          strokeDasharray={`${(value / 100) * circumference} ${circumference}`}
          transform={`rotate(-90 ${center} ${center})`}
        />
      )}
    </svg>
  )
}
