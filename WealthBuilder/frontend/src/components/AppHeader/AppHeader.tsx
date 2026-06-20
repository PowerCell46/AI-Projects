import { Link, NavLink } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext/useAuth';
import { ThemeToggle } from '../ThemeToggle/ThemeToggle';
import { APP_ROUTES } from '../../constants/routes';
import styles from './AppHeader.module.css';


/**
 * Shared top bar for every authenticated screen: the brand (home link), moderator-only
 * manage-assets link, theme toggle, and logout. Keeps the chrome identical across pages.
 */
export const AppHeader = () => {
    const { user, logout } = useAuth();

    const isModerator = user?.role === 'MODERATOR';

    return (
        <header className={styles.header}>
            <div className={styles.left}>
                <Link className={styles.brand} to={APP_ROUTES.HOME}>▮ WEALTHBUILDER</Link>

                {user && (
                    <span className={styles.identity}>
                        {user.username} <span className={styles.role}>· {user.role}</span>
                    </span>
                )}
            </div>

            <nav className={styles.nav}>
                {isModerator && (
                    <NavLink
                        to={APP_ROUTES.ADMIN_ASSETS}
                        className={({ isActive }) => (isActive ? `${styles.navLink} ${styles.navLinkActive}` : styles.navLink)}
                    >
                        manage assets
                    </NavLink>
                )}

                <ThemeToggle />

                <button type="button" className={styles.logout} onClick={logout}>
                    log out
                </button>
            </nav>
        </header>
    );
};
