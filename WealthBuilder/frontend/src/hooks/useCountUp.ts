import { useEffect, useRef, useState } from 'react';
import { usePrefersReducedMotion } from './usePrefersReducedMotion';


// Long enough to read as a deliberate spin-up, short enough not to stall the page.
const DEFAULT_DURATION_MS = 4200;


/**
 * Gentle deceleration curve: the value climbs steadily, then eases into its final figure —
 * the slot-machine "settle" where the reels coast to a stop.
 */
const easeOutCubic = (fraction: number): number => {
    return 1 - Math.pow(1 - fraction, 3);
};


/**
 * Animates a number from 0 up to {@link target} with a slot-machine settle, beginning only
 * once {@link enabled} flips true (so callers can wait for an entrance animation to finish).
 * Honours prefers-reduced-motion by jumping straight to the target.
 */
export const useCountUp = (
    target: number,
    enabled: boolean,
    durationMs: number = DEFAULT_DURATION_MS,
): number => {
    const prefersReducedMotion = usePrefersReducedMotion();

    const [value, setValue] = useState(0);
    const frameRef = useRef<number | null>(null);
    const startTimeRef = useRef<number | null>(null);

    useEffect(() => {
        if (!enabled || prefersReducedMotion) {
            return;
        }

        startTimeRef.current = null;

        const tick = (timestamp: number): void => {
            if (startTimeRef.current === null) {
                startTimeRef.current = timestamp;
            }

            const elapsed = timestamp - startTimeRef.current;
            const fraction = Math.min(elapsed / durationMs, 1);

            setValue(target * easeOutCubic(fraction));

            if (fraction < 1) {
                frameRef.current = requestAnimationFrame(tick);

                return;
            }

            setValue(target);
        };

        frameRef.current = requestAnimationFrame(tick);

        return () => {
            if (frameRef.current !== null) {
                cancelAnimationFrame(frameRef.current);
                frameRef.current = null;
            }
        };
    }, [target, enabled, durationMs, prefersReducedMotion]);

    if (!enabled) {
        return 0;
    }

    if (prefersReducedMotion) {
        return target;
    }

    return value;
};
