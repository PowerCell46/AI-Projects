import { useEffect, useRef, useState } from 'react';
import { useSweepClock } from './useSweepClock';
import { usePrefersReducedMotion } from './usePrefersReducedMotion';


// Deliberately slower than the login <-> register swap (800ms): this is the headline
// transition onto the home screen and wants room to breathe.
const ENTRANCE_DURATION_MS = 1600;


export interface EntranceReveal {
    // True while the covering layer should be mounted over the page content.
    isRevealing: boolean;
    // Reveal clock, 0 (fully covered) -> 1 (fully revealed).
    progress: number;
}


/**
 * Plays the VHS sweep as a one-shot entrance: the page mounts behind a covering layer that
 * retracts top-down to reveal it. Runs once when enabled, and skips entirely under
 * prefers-reduced-motion so the page simply appears.
 */
export const useEntranceReveal = (enabled: boolean): EntranceReveal => {
    const prefersReducedMotion = usePrefersReducedMotion();

    // Latch the decision on first render: the caller may clear its trigger mid-sweep, and
    // the reveal must keep running until the clock completes.
    const [shouldPlay] = useState(enabled && !prefersReducedMotion);

    const [isDone, setIsDone] = useState(false);
    const { progress, start } = useSweepClock(() => setIsDone(true));
    const hasStartedRef = useRef(false);

    useEffect(() => {
        if (hasStartedRef.current || !shouldPlay) {
            return;
        }

        hasStartedRef.current = true;
        start(ENTRANCE_DURATION_MS);
    }, [shouldPlay, start]);

    return { isRevealing: shouldPlay && !isDone, progress };
};
