import { Link } from 'react-router-dom'
import { usersApi } from '../../api/users'
import { ErrorAlert } from '../../components/ErrorAlert'
import { EmptyState, Loading, StatusBadge } from '../../components/Feedback'
import { useLoader } from '../../hooks/useLoader'

export function UsersPage() {
  const { data: users, error, loading, reload } = useLoader(() => usersApi.list(), [])

  return (
    <section>
      <div className="page-header">
        <h1>Users</h1>
        <Link to="/users/new" className="button">
          New user
        </Link>
      </div>

      {loading && <Loading label="Loading users…" />}

      {!loading && error !== null && (
        <>
          <ErrorAlert error={error} />
          <button type="button" className="button-secondary" onClick={reload}>
            Retry
          </button>
        </>
      )}

      {!loading && users && users.length === 0 && (
        <EmptyState>
          <p>No users yet.</p>
          <Link to="/users/new" className="button">
            Create the first user
          </Link>
        </EmptyState>
      )}

      {!loading && users && users.length > 0 && (
        <div className="table-wrapper">
          <table>
            <thead>
              <tr>
                <th className="numeric">ID</th>
                <th>Name</th>
                <th>Email</th>
                <th>Document</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {users.map((user) => (
                <tr key={user.id}>
                  <td className="numeric">{user.id}</td>
                  <td>
                    <Link to={`/users/${user.id}`}>{user.name}</Link>
                  </td>
                  <td>{user.email}</td>
                  <td>{user.document}</td>
                  <td>
                    <StatusBadge status={user.status} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
