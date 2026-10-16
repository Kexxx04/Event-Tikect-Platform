import { useState } from 'react'
import { usersApi } from '../../api/users'
import { Avatar } from '../../components/Avatar'
import { Button, ButtonLink } from '../../components/Button'
import { Card } from '../../components/Card'
import { DataTable } from '../../components/DataTable'
import { ErrorAlert } from '../../components/ErrorAlert'
import { EmptyState, Loading } from '../../components/Feedback'
import { PlusIcon } from '../../components/icons'
import { PageHeader } from '../../components/PageHeader'
import { SearchInput } from '../../components/SearchInput'
import { StatTile } from '../../components/StatTile'
import { StatusBadge } from '../../components/StatusBadge'
import { useLoader } from '../../hooks/useLoader'
import styles from './UsersPage.module.css'

// The API returns every user, so search and pagination happen here.
const PAGE_SIZE = 10

export function UsersPage() {
  const { data: users, error, loading, reload } = useLoader(() => usersApi.list(), [])
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)

  const search = query.trim().toLowerCase()
  const matches = (users ?? []).filter((user) =>
    [user.name, user.email, user.document].some((value) => value.toLowerCase().includes(search)),
  )
  const pageCount = Math.max(1, Math.ceil(matches.length / PAGE_SIZE))
  const currentPage = Math.min(page, pageCount - 1)
  const pageItems = matches.slice(currentPage * PAGE_SIZE, (currentPage + 1) * PAGE_SIZE)
  const activeCount = (users ?? []).filter((user) => user.status === 'ACTIVE').length

  function updateQuery(value: string) {
    setQuery(value)
    setPage(0)
  }

  return (
    <section>
      <PageHeader
        title="Users"
        subtitle="People who can register to events. Inactive users cannot register."
        actions={
          <ButtonLink to="/users/new">
            <PlusIcon size={18} />
            New user
          </ButtonLink>
        }
      />

      {loading && <Loading label="Loading users…" />}

      {!loading && error !== null && (
        <>
          <ErrorAlert error={error} />
          <Button variant="secondary" onClick={reload}>
            Retry
          </Button>
        </>
      )}

      {!loading && users && users.length === 0 && (
        <EmptyState>
          <p>No users yet.</p>
          <ButtonLink to="/users/new">Create the first user</ButtonLink>
        </EmptyState>
      )}

      {!loading && users && users.length > 0 && (
        <>
          <div className={styles.tiles}>
            <StatTile label="Total users" value={users.length} />
            <StatTile label="Active" value={activeCount} valueTone="active" />
            <StatTile label="Inactive" value={users.length - activeCount} valueTone="muted" />
          </div>

          <Card as="section" aria-label="User list">
            <div className={styles.toolbar}>
              <SearchInput
                id="user-search"
                label="Search users"
                placeholder="Search by name, email or document"
                value={query}
                onChange={updateQuery}
              />
            </div>

            {matches.length === 0 ? (
              <EmptyState>
                <p>No users match “{query.trim()}”.</p>
              </EmptyState>
            ) : (
              <>
                <DataTable label="Users">
                  <thead>
                    <tr>
                      <th scope="col">Name</th>
                      <th scope="col">Email</th>
                      <th scope="col">Document</th>
                      <th scope="col">Status</th>
                      <th scope="col">
                        <span className="visually-hidden">Actions</span>
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {pageItems.map((user) => (
                      <tr key={user.id}>
                        <td>
                          <span className={styles.person}>
                            <Avatar name={user.name} />
                            <span className={styles.name}>{user.name}</span>
                          </span>
                        </td>
                        <td className={styles.muted}>{user.email}</td>
                        <td className={styles.muted}>{user.document}</td>
                        <td>
                          <StatusBadge status={user.status} />
                        </td>
                        <td data-align="end">
                          <ButtonLink to={`/users/${user.id}`} variant="secondary" aria-label={`View ${user.name}`}>
                            View
                          </ButtonLink>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </DataTable>

                <div className={styles.pagination}>
                  <p className={styles.muted}>
                    Showing {pageItems.length} of {matches.length}
                  </p>
                  <div className={styles.pageButtons}>
                    <Button
                      variant="secondary"
                      onClick={() => setPage(currentPage - 1)}
                      disabled={currentPage === 0}
                    >
                      Previous
                    </Button>
                    <Button
                      variant="secondary"
                      onClick={() => setPage(currentPage + 1)}
                      disabled={currentPage >= pageCount - 1}
                    >
                      Next
                    </Button>
                  </div>
                </div>
              </>
            )}
          </Card>
        </>
      )}
    </section>
  )
}
