import type { EventAvailability } from '../../api/events'
import { OccupancyRing } from '../../components/OccupancyRing'
import { StatTile } from '../../components/StatTile'
import styles from './AvailabilityTiles.module.css'

export function AvailabilityTiles({ availability }: { availability: EventAvailability }) {
  const { capacity, registeredCount, availableSeats, waitlistCount, acceptingRegistrations } = availability
  const occupied = Math.min(registeredCount, capacity)
  const percentage = capacity > 0 ? Math.round((occupied / capacity) * 100) : 0
  const isFull = availableSeats === 0

  const seatsCaption = !acceptingRegistrations
    ? 'Registrations closed'
    : isFull
      ? 'New registrations join the waitlist'
      : 'Accepting registrations'

  return (
    <div className={styles.tiles}>
      <StatTile
        label="Occupancy"
        value={`${percentage}%`}
        caption={`${registeredCount} / ${capacity} seats taken`}
        visual={<OccupancyRing percentage={percentage} full={isFull} />}
      />
      <StatTile
        label="Seats available"
        value={availableSeats}
        valueTone="accent"
        caption={seatsCaption}
        captionTone={!acceptingRegistrations ? 'muted' : isFull ? 'waitlist' : 'active'}
      />
      <StatTile label="Waitlist" value={waitlistCount} valueTone="waitlist" caption="Promoted automatically" />
    </div>
  )
}
