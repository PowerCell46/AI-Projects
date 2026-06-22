import { useCallback, useEffect, useRef } from 'react';
import { useSweepClock } from './useSweepClock';
import { usePrefersReducedMotion } from './usePrefersReducedMotion';


// Matches the app-wide view-change sweep so every table reveal feels like the same animation.
const SWEEP_DURATION_MS = 1100;

const NO_OP = (): void => undefined;


export interface SweepOnChange {
    // Sweep clock, 0 -> 1, for positioning the cover/bands.
    progress: number;
    // True while a sweep is playing.
    isRunning: boolean;
    // Replays the sweep on demand (e.g. after a delete that mutates the visible set in place).
    replay: () => void;
}


/**
 * Replays the scanline sweep whenever `dependency` changes identity (a new page, a fresh result
 * set), honouring prefers-reduced-motion. The ref guard stops the effect re-firing when `start`
 * merely changes identity as the clock starts and stops, which would otherwise loop forever.
 */
export const useSweepOnChange = <T>(dependency: T): SweepOnChange => {
    const prefersReducedMotion = usePrefersReducedMotion();
    const { progress, isRunning, start } = useSweepClock(NO_OP);
    const previousRef = useRef(dependency);

    useEffect(() => {
        const changed = previousRef.current !== dependency;
        previousRef.current = dependency;

        if (changed && !prefersReducedMotion) {
            start(SWEEP_DURATION_MS);
        }
    }, [dependency, prefersReducedMotion, start]);

    const replay = useCallback(() => {
        if (!prefersReducedMotion) {
            start(SWEEP_DURATION_MS);
        }
    }, [prefersReducedMotion, start]);

    return { progress, isRunning, replay };
};
