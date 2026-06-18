import { useEffect, useState } from 'react';
import { useAuth } from '../../context/AuthContext/useAuth';
import { useEntranceReveal } from '../../hooks/useEntranceReveal';
import { ThemeToggle } from '../ThemeToggle/ThemeToggle';
import { VhsBands } from '../VhsBands/VhsBands';
import styles from './HomePage.module.css';


const CURRENCY_FORMATTER = new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
});


/**
 * Placeholder authenticated landing. The full dashboard (donut + carousel) is future
 * scope per PLAN.md; for now it confirms the session and shows the computed balance.
 */
export const HomePage = () => {
    const { user, logout, justAuthenticated, clearJustAuthenticated } = useAuth();

    // Capture the entrance trigger once on mount, then clear it so a later refresh of the
    // home screen doesn't replay the sweep.
    const [enteredFromAuth] = useState(justAuthenticated);
    const { isRevealing, progress } = useEntranceReveal(enteredFromAuth);

    useEffect(() => {
        if (enteredFromAuth) {
            clearJustAuthenticated();
        }
    }, [enteredFromAuth, clearJustAuthenticated]);

    if (user === null) {
        return null;
    }

    return (
        <div className={styles.page}>
            {isRevealing && (
                <>
                    <div
                        className={styles.entranceCover}
                        style={{ clipPath: `inset(${progress * 100}% 0 0 0)` }}
                        aria-hidden="true"
                    />

                    <VhsBands progress={progress} />
                </>
            )}

            <header className={styles.header}>
                <span className={styles.brand}>▮ WEALTHBUILDER</span>

                <div className={styles.actions}>
                    <ThemeToggle />

                    <button type="button" className={styles.logout} onClick={logout}>
                        log out
                    </button>
                </div>
            </header>

            <main className={styles.main}>
                <p className={styles.greeting}>
                    Signed in as <strong>{user.username}</strong> · {user.role}
                </p>

                <section className={styles.balanceCard}>
                    <span className={styles.balanceLabel}>NET INVESTED</span>
                    <span className={styles.balanceValue}>
                        {CURRENCY_FORMATTER.format(user.balance)}
                    </span>
                </section>
            </main>
        </div>
    );
};
