import { Outlet } from 'react-router-dom'
import { Sidebar } from '../Sidebar/Sidebar'
import styles from './AppLayout.module.css'


/** Two-column shell: 300px sidebar + fluid main, sharing one background. */
export const AppLayout = () => (
  <div className={styles.layout}>
    <Sidebar />
    <main className={styles.main}>
      <Outlet />
    </main>
  </div>
)
