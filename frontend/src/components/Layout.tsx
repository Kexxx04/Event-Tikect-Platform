import { NavLink, Outlet } from 'react-router-dom'

export function Layout() {
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
        </div>
      </header>
      <main className="container page">
        <Outlet />
      </main>
    </>
  )
}
