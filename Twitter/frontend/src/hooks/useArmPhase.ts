import { useEffect, useRef, useState } from 'react';


const PHASE_HOLD_MS = 3000;

export type ArmPhase = 'idle' | 'armed' | 'failed';

interface ArmPhaseControl {
    phase: ArmPhase;
    arm: () => void;
    markFailed: () => void;
    reset: () => void;
}

// The two looks a button holds for three seconds before it shows the real state again: armed (a second tap confirms,
// as unfollowing or discarding changes does) and failed (the change did not go through). Entering one cancels the timer
// of the other.
export function useArmPhase(): ArmPhaseControl {
    const [phase, setPhase] = useState<ArmPhase>('idle');
    const timerRef = useRef<number | null>(null);

    function clearTimer() {
        if (timerRef.current !== null) {
            window.clearTimeout(timerRef.current);
            timerRef.current = null;
        }
    }

    useEffect(() => clearTimer, []);

    function reset() {
        clearTimer();
        setPhase('idle');
    }

    function enter(nextPhase: ArmPhase) {
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
