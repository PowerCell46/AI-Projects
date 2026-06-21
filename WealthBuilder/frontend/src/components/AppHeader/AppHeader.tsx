import { Link, NavLink } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext/useAuth';
import { useViewTransition } from '../../hooks/useViewTransition';
import { ThemeToggle } from '../ThemeToggle/ThemeToggle';
import { APP_ROUTES } from '../../constants/routes';
import styles from './AppHeader.module.css';


/**
 * Shared top bar for every authenticated screen: the brand (home link), moderator-only
 * manage-assets link, theme toggle, and logout. Keeps the chrome identical across pages.
 */
export const AppHeader = () => {
    const { user, logout } = useAuth();
    const { play } = useViewTransition();

    const isModerator = user?.role === 'MODERATOR';

    // Play the reversed (bottom-up) green exit sweep over the screen, then clear the session —
    // the cover hides the swap and retracts to reveal the login screen behind it.
    const handleLogout = (): void => {
        play('exit');
        logout();
    };

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

                <button type="button" className={styles.logout} onClick={handleLogout}>
                    log out
                </button>
            </nav>
        </header>
    );
};
