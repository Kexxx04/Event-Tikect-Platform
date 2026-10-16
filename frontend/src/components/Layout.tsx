import { Link, NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { Avatar } from './Avatar'
import { CalendarIcon, SignOutIcon, UsersIcon } from './icons'
import styles from './Layout.module.css'

function navClass({ isActive }: { isActive: boolean }): string {
  return isActive ? `${styles.navLink} ${styles.active}` : styles.navLink
}

/** App shell: white sidebar (top bar on mobile) with the navigation and the signed-in admin. */
export function Layout() {
  const { session, logout } = useAuth()

  return (
    <div className={styles.shell}>
      <aside className={styles.sidebar}>
        <Link to="/events" className={styles.brand}>
          <span className={styles.logo}>
            <CalendarIcon size={22} />
          </span>
          Event Platform
        </Link>

        <nav className={styles.nav} aria-label="Main">
          <NavLink to="/events" className={navClass}>
            <CalendarIcon />
            Events
          </NavLink>
          <NavLink to="/users" className={navClass}>
            <UsersIcon />
            Users
          </NavLink>
        </nav>

        {session && (
          <div className={styles.user}>
            <Avatar name={session.username} size="md" />
            <div className={styles.userText}>
              <span className={styles.userName}>{session.username}</span>
              <span className={styles.userRole}>Admin</span>
            </div>
            <button type="button" className={styles.signOut} onClick={logout} aria-label="Sign out" title="Sign out">
              <SignOutIcon />
            </button>
          </div>
        )}
      </aside>

      <main className={styles.main}>
        <div className={styles.content}>
          <Outlet />
        </div>
      </main>
    </div>
  )
}
