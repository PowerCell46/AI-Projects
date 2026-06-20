import { useEffect, useState } from 'react';
import { useAuth } from '../../context/AuthContext/useAuth';
import { useEntranceReveal } from '../../hooks/useEntranceReveal';
import { AppHeader } from '../AppHeader/AppHeader';
import { AssetCarousel } from '../AssetCarousel/AssetCarousel';
import { VhsBands } from '../VhsBands/VhsBands';
import styles from './HomePage.module.css';


const CURRENCY_FORMATTER = new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
});


/**
 * Authenticated landing: the computed balance plus the asset carousel. The invested-per-asset
 * donut is future scope per PLAN.md — it needs the dashboard distribution endpoint.
 */
export const HomePage = () => {
    const { user, justAuthenticated, clearJustAuthenticated } = useAuth();

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

            <AppHeader />

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

                <section className={styles.assets}>
                    <h2 className={styles.sectionHeading}>ASSETS</h2>
                    <AssetCarousel />
                </section>
            </main>
        </div>
    );
};
