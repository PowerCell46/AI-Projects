import { useCallback, useLayoutEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import { TransitionContext } from './TransitionContext';
import type { SweepVariant, TransitionContextValue } from './TransitionContext';
import { useSweepClock } from '../../hooks/useSweepClock';
import { usePrefersReducedMotion } from '../../hooks/usePrefersReducedMotion';
import { VhsBands } from '../../components/VhsBands/VhsBands';
import { APP_ROUTES } from '../../constants/routes';
import styles from './TransitionProvider.module.css';


// Shorter than the login entrance (1600ms): this fires on every view change, so it wants to
// feel snappy rather than headline.
const TRANSITION_DURATION_MS = 1100;

const ASSET_DETAIL_PREFIX = APP_ROUTES.ASSET_DETAIL.replace(':slug', '');


interface TransitionProviderProps {
    children: ReactNode;
}


/**
 * Owns the single VHS sweep used for view changes. It fires automatically when navigating
 * between the authenticated app views, and exposes play() so in-page view swaps that keep the
 * same URL (the asset admin list <-> form) can trigger the identical animation. Stays off
 * across the auth boundary and under prefers-reduced-motion.
 */
export const TransitionProvider = ({ children }: TransitionProviderProps) => {
    const { pathname } = useLocation();
    const prefersReducedMotion = usePrefersReducedMotion();

    const { progress, isRunning, start } = useSweepClock(NO_OP);
    const previousPathRef = useRef(pathname);
    const [variant, setVariant] = useState<SweepVariant>('standard');

    const play = useCallback((nextVariant: SweepVariant = 'standard') => {
        if (!prefersReducedMotion) {
            setVariant(nextVariant);
            start(TRANSITION_DURATION_MS);
        }
    }, [prefersReducedMotion, start]);

    // useLayoutEffect so the cover is in place before the browser paints the new route,
    // avoiding a one-frame flash of the destination view.
    useLayoutEffect(() => {
        const previousPath = previousPathRef.current;
        previousPathRef.current = pathname;

        if (prefersReducedMotion) {
            return;
        }

        if (isInternalNavigation(previousPath, pathname)) {
            setVariant('standard');
            start(TRANSITION_DURATION_MS);
        }
    }, [pathname, prefersReducedMotion, start]);

    const value = useMemo<TransitionContextValue>(() => ({ play }), [play]);

    return (
        <TransitionContext.Provider value={value}>
            {isRunning && (
                <div
                    className={`${styles.overlay} ${variant === 'exit' ? styles.exit : ''}`}
                    aria-hidden="true"
                >
                    <div
                        className={styles.cover}
                        style={{ clipPath: `inset(${progress * 100}% 0 0 0)` }}
                    />

                    <VhsBands progress={progress} />
                </div>
            )}

            {children}
        </TransitionContext.Provider>
    );
};


/** A navigation that both starts and ends on an in-app view (not from/to the auth screens). */
const isInternalNavigation = (previousPath: string, nextPath: string): boolean => {
    return previousPath !== nextPath
        && isAppView(previousPath)
        && isAppView(nextPath);
};

const isAppView = (pathname: string): boolean => {
    return pathname === APP_ROUTES.HOME
        || pathname === APP_ROUTES.ADMIN_ASSETS
        || pathname.startsWith(ASSET_DETAIL_PREFIX);
};

const NO_OP = (): void => undefined;
