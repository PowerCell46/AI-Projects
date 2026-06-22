import styles from './SessionLoader.module.css';


/**
 * Full-viewport terminal-style loader shown while the auth session rehydrates. Shared by the
 * route guards so every gated route presents the same waiting state instead of a blank screen.
 */
export const SessionLoader = () => {
    return <div className={styles.loader}>booting…</div>;
};
