import { useCallback, useEffect, useRef, useState } from 'react';


export interface SweepClock {
    // Sweep progress, 0 -> 1. Only meaningful while a sweep is running.
    progress: number;
    // True from start() until the clock reaches 1.
    isRunning: boolean;
    // Begins a fresh sweep over the given duration (no-op while one is in flight).
    start: (durationMs: number) => void;
}


/**
 * A single requestAnimationFrame clock that ramps progress 0 -> 1 over a caller-supplied
 * duration, then fires onComplete once. Every VHS-style sweep in the app shares this hook,
 * so the frame loop and its timing live in exactly one place.
 */
export const useSweepClock = (onComplete: () => void): SweepClock => {
    const [progress, setProgress] = useState(0);
    const [isRunning, setIsRunning] = useState(false);

    const durationRef = useRef(0);
    const startRef = useRef<number | null>(null);
    const frameRef = useRef<number | null>(null);

    // Always invoke the latest callback without retriggering the frame-loop effect.
    const onCompleteRef = useRef(onComplete);

    useEffect(() => {
        onCompleteRef.current = onComplete;
    });

    const start = useCallback((durationMs: number) => {
        if (isRunning) {
            return;
        }

        durationRef.current = durationMs;
        startRef.current = null;
        setProgress(0);
        setIsRunning(true);
    }, [isRunning]);

    useEffect(() => {
        if (!isRunning) {
            return;
        }

        const tick = (timestamp: number): void => {
            if (startRef.current === null) {
                startRef.current = timestamp;
            }

            const elapsed = timestamp - startRef.current;
            const nextProgress = Math.min(elapsed / durationRef.current, 1);

            setProgress(nextProgress);

            if (nextProgress < 1) {
                frameRef.current = requestAnimationFrame(tick);

                return;
            }

            setIsRunning(false);
            onCompleteRef.current();
        };

        frameRef.current = requestAnimationFrame(tick);

        return () => {
            if (frameRef.current !== null) {
                cancelAnimationFrame(frameRef.current);
                frameRef.current = null;
            }
            // Reset so the clock starts clean if the component remounts (e.g. StrictMode).
            setIsRunning(false);
        };
    }, [isRunning]);

    return { progress, isRunning, start };
};
