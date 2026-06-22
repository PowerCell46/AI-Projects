import { useEffect, useState } from 'react';
import { useAuth } from '../../context/AuthContext/useAuth';
import { useCountUp } from '../../hooks/useCountUp';
import { useEntranceReveal } from '../../hooks/useEntranceReveal';
import { AppHeader } from '../AppHeader/AppHeader';
import { AssetCarousel } from '../AssetCarousel/AssetCarousel';
import { DistributionChart } from '../DistributionChart/DistributionChart';
import { PlatformLinks } from '../PlatformLinks/PlatformLinks';
import { VhsBands } from '../VhsBands/VhsBands';
import { formatMoney } from '../../utils/format';
import styles from './HomePage.module.css';


/**
 * Authenticated landing: an intro + computed balance, the asset carousel, external platform
 * shortcuts, and the invested-per-asset distribution donut.
 */
export const HomePage = () => {
    const { user, justAuthenticated, clearJustAuthenticated, refreshUser } = useAuth();

    // Capture the entrance trigger once on mount, then clear it so a later refresh of the
    // home screen doesn't replay the sweep.
    const [enteredFromAuth] = useState(justAuthenticated);
    const { isRevealing, progress } = useEntranceReveal(enteredFromAuth);

    // Hold the balance at 00.00 behind the entrance cover, then spin it up once the page is
    // revealed (or immediately when there's no entrance animation to wait for).
    const animatedBalance = useCountUp(user?.balance ?? 0, !isRevealing);

    useEffect(() => {
        if (enteredFromAuth) {
            clearJustAuthenticated();
        }
    }, [enteredFromAuth, clearJustAuthenticated]);

    // Re-read the balance every time the dashboard is shown, so a holding edited on a detail
    // screen is reflected here without a manual page reload.
    useEffect(() => {
        void refreshUser();
    }, [refreshUser]);

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
                <section className={styles.intro}>
                    <div className={styles.introText}>
                        <h1 className={styles.introTitle}>One platform, every investment.</h1>
                        <p className={styles.introLead}>
                            WealthBuilder is a personal holdings tracker. Record what you buy across
                            stocks, crypto, and precious metals, watch your net invested balance, and
                            see how your money is split across asset classes.
                        </p>
                    </div>

                    <section className={styles.balanceCard}>
                        <span className={styles.balanceLabel}>NET INVESTED</span>
                        <span className={styles.balanceValue}>
                            {formatMoney(animatedBalance)}
                        </span>
                    </section>
                </section>

                <section className={styles.assets}>
                    <h2 className={styles.sectionHeading}>ASSETS</h2>
                    <AssetCarousel />
                </section>

                <section className={styles.section}>
                    <h2 className={styles.sectionHeading}>PLATFORMS</h2>
                    <PlatformLinks />
                </section>

                <section className={styles.section}>
                    <h2 className={styles.sectionHeading}>DISTRIBUTION</h2>
                    <DistributionChart />
                </section>
            </main>
        </div>
    );
};
