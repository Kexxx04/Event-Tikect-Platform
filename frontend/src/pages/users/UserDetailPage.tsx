import { useState } from 'react'
import { useLocation, useParams } from 'react-router-dom'
import { usersApi, type UserStatus } from '../../api/users'
import { Avatar } from '../../components/Avatar'
import { Breadcrumb } from '../../components/Breadcrumb'
import { Button, ButtonLink } from '../../components/Button'
import { Card } from '../../components/Card'
import { ConfirmPanel } from '../../components/ConfirmPanel'
import { ErrorAlert } from '../../components/ErrorAlert'
import { Loading, SuccessAlert } from '../../components/Feedback'
import { StatusBadge } from '../../components/StatusBadge'
import { useLoader } from '../../hooks/useLoader'
import styles from './UserDetailPage.module.css'
import { UserEventsCard } from './UserEventsCard'

function noticeFrom(state: unknown): string | null {
  const notice = (state as { notice?: unknown } | null)?.notice
  return typeof notice === 'string' ? notice : null
}

export function UserDetailPage() {
  const { id } = useParams()
  const userId = Number(id)
  const location = useLocation()
  const { data: user, error, loading, setData } = useLoader(() => usersApi.get(userId), [userId])
  const [notice, setNotice] = useState<string | null>(() => noticeFrom(location.state))
  const [actionError, setActionError] = useState<unknown>(null)
  const [confirmingDeactivate, setConfirmingDeactivate] = useState(false)
  const [saving, setSaving] = useState(false)

  async function changeStatus(status: UserStatus) {
    setNotice(null)
    setActionError(null)
    setSaving(true)
    try {
      setData(await usersApi.updateStatus(userId, status))
      setConfirmingDeactivate(false)
      setNotice(status === 'ACTIVE' ? 'User activated' : 'User deactivated')
    } catch (err) {
      setActionError(err)
    } finally {
      setSaving(false)
    }
  }

  if (loading && !user) return <Loading label="Loading user…" />

  if (error || !user) {
    return (
      <section>
        <Breadcrumb items={[{ label: 'Users', to: '/users' }, { label: 'User' }]} />
        <ErrorAlert error={error} />
      </section>
    )
  }

  return (
    <section>
      <Breadcrumb items={[{ label: 'Users', to: '/users' }, { label: user.name }]} />

      <Card className={styles.header}>
        <Avatar name={user.name} size="lg" />
        <div className={styles.info}>
          <div className={styles.titleRow}>
            <h1 className={styles.title}>{user.name}</h1>
            <StatusBadge status={user.status} />
          </div>
          <p className={styles.meta}>
            {user.email} · Doc. {user.document}
          </p>
        </div>
        <div className={styles.actions}>
          <ButtonLink to={`/users/${user.id}/edit`} variant="secondary">
            Edit
          </ButtonLink>
          {user.status === 'ACTIVE' ? (
            <Button variant="danger" onClick={() => setConfirmingDeactivate(true)} disabled={confirmingDeactivate}>
              Deactivate
            </Button>
          ) : (
            <Button onClick={() => void changeStatus('ACTIVE')} loading={saving}>
              Activate
            </Button>
          )}
        </div>
      </Card>

      {confirmingDeactivate && (
        <ConfirmPanel
          title={`Deactivate ${user.name}?`}
          confirmLabel="Deactivate"
          onConfirm={() => void changeStatus('INACTIVE')}
          onCancel={() => setConfirmingDeactivate(false)}
          busy={saving}
        >
          Inactive users cannot register to new events. Their current registrations are kept.
        </ConfirmPanel>
      )}

      <SuccessAlert message={notice} />
      <ErrorAlert error={actionError} />

      <UserEventsCard userId={user.id} />
    </section>
  )
}
