import type { EventAvailability } from '../../api/events'

export function AvailabilityCard({ availability }: { availability: EventAvailability }) {
  const { capacity, registeredCount, availableSeats, waitlistCount, acceptingRegistrations } = availability
  const occupied = Math.min(registeredCount, capacity)
  const percentage = capacity > 0 ? Math.round((occupied / capacity) * 100) : 0
  const isFull = availableSeats === 0

  return (
    <div className="card">
      <h2>Availability</h2>

      <div className="availability-figure">
        <span className="availability-number">{availableSeats}</span>
        <span>{availableSeats === 1 ? 'seat available' : 'seats available'}</span>
      </div>

      <div className="occupancy-label">
        <span>Occupancy</span>
        <span>
          {registeredCount} / {capacity} ({percentage}%)
        </span>
      </div>
      <progress
        className={isFull ? 'occupancy full' : 'occupancy'}
        max={capacity}
        value={occupied}
        aria-label={`Occupancy ${percentage}%`}
      />

      <dl className="details">
        <dt>Waitlist</dt>
        <dd>{waitlistCount}</dd>
        <dt>Accepting registrations</dt>
        <dd>
          <span className={acceptingRegistrations ? 'badge badge-yes' : 'badge badge-no'}>
            {acceptingRegistrations ? 'YES' : 'NO'}
          </span>
        </dd>
      </dl>
    </div>
  )
}
