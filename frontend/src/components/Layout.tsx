import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export function Layout() {
  const { session, logout } = useAuth()

  return (
    <>
      <header className="navbar">
        <div className="container navbar-inner">
          <NavLink to="/" className="brand">
            Event Platform
          </NavLink>
          <nav className="nav-links" aria-label="Main">
            <NavLink to="/users">Users</NavLink>
            <NavLink to="/events">Events</NavLink>
          </nav>
          {session && (
            <div className="navbar-user">
              <span className="navbar-username" title="Signed in as">
                {session.username}
              </span>
              <button type="button" className="button-ghost" onClick={logout}>
                Logout
              </button>
            </div>
          )}
        </div>
      </header>
      <main className="container page">
        <Outlet />
      </main>
    </>
  )
}
