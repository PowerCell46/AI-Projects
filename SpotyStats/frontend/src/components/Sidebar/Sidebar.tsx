import { NavLink } from 'react-router-dom'
import styles from './Sidebar.module.css'


interface NavItem {
  label: string
  path: string
}

const NAV_ITEMS: readonly NavItem[] = [
  { label: 'Overview', path: '/overview' },
  { label: 'History', path: '/history' },
  { label: 'Artists', path: '/artists' },
  { label: 'Liked songs', path: '/liked-songs' },
  { label: 'Insights', path: '/insights' },
  { label: 'Profile', path: '/profile' },
]

export const Sidebar = () => (
  <aside className={styles.sidebar}>
    <div className={styles.wordmark}>Spotystats</div>
    <nav className={styles.nav}>
      {NAV_ITEMS.map((item) => (
        <NavLink
          key={item.path}
          to={item.path}
          className={({ isActive }) => (isActive ? styles.navItemActive : styles.navItem)}
        >
          {item.label}
        </NavLink>
      ))}
    </nav>
  </aside>
)
