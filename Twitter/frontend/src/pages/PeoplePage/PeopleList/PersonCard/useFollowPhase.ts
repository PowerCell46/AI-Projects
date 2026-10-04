import { useEffect, useRef, useState } from 'react';


export type FollowPhase = 'idle' | 'armed' | 'failed';

const PHASE_HOLD_MS = 3000;

interface FollowPhaseControl {
    phase: FollowPhase;
    arm: () => void;
    markFailed: () => void;
    reset: () => void;
}

// The two looks the follow button holds for three seconds before it shows the real state again: armed (a second tap
// unfollows) and failed (the change did not go through). Entering one cancels the timer of the other.
export function useFollowPhase(): FollowPhaseControl {
    const [phase, setPhase] = useState<FollowPhase>('idle');
    const timerRef = useRef<number | null>(null);

    useEffect(() => {
        return () => {
            if (timerRef.current !== null) {
                window.clearTimeout(timerRef.current);
            }
        };
    }, []);

    function clearTimer() {
        if (timerRef.current !== null) {
            window.clearTimeout(timerRef.current);
            timerRef.current = null;
        }
    }

    function reset() {
        clearTimer();
        setPhase('idle');
    }

    function enter(nextPhase: FollowPhase) {
        clearTimer();
        setPhase(nextPhase);
        timerRef.current = window.setTimeout(reset, PHASE_HOLD_MS);
    }

    return {
        phase,
        arm: () => enter('armed'),
        markFailed: () => enter('failed'),
        reset,
    };
}
