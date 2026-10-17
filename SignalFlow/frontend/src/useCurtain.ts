import { useEffect, useRef, useState } from 'react';
import type { CurtainDirection, CurtainPhase } from './components/SuccessCurtain/SuccessCurtain';

// The cover and reveal durations must match the 340ms animations in SuccessCurtain.css, or the
// swap behind the curtain happens mid-animation.
const CURTAIN_COVER_MS = 340;

const CURTAIN_HOLD_MS = 1400;

const CURTAIN_REVEAL_MS = 340;

export interface CurtainGreeting {
    heading: string;
    sub: string;
}

export interface Curtain {
    phase: CurtainPhase | null;
    direction: CurtainDirection;
    greeting: CurtainGreeting | null;
    isPlaying: boolean;
    play: (direction: CurtainDirection, greeting: CurtainGreeting, onCovered: () => void) => void;
}

export function useCurtain(): Curtain {
    const [phase, setPhase] = useState<CurtainPhase | null>(null);
    const [direction, setDirection] = useState<CurtainDirection>('up');
    const [greeting, setGreeting] = useState<CurtainGreeting | null>(null);

    const timeoutIdsRef = useRef<number[]>([]);

    useEffect(() => {
        const timeoutIds = timeoutIdsRef.current;

        return () => {
            timeoutIds.forEach((timeoutId) => window.clearTimeout(timeoutId));
        };
    }, []);

    function play(nextDirection: CurtainDirection, nextGreeting: CurtainGreeting, onCovered: () => void) {
        setGreeting(nextGreeting);
        setDirection(nextDirection);
        setPhase('covering');

        const coveredId = window.setTimeout(
            () => {
                onCovered();
                setPhase('covered');
            },
            CURTAIN_COVER_MS,
        );

        const revealId = window.setTimeout(
            () => setPhase('revealing'),
            CURTAIN_COVER_MS + CURTAIN_HOLD_MS,
        );

        const doneId = window.setTimeout(
            () => {
                setPhase(null);
                setGreeting(null);
            },
            CURTAIN_COVER_MS + CURTAIN_HOLD_MS + CURTAIN_REVEAL_MS,
        );

        timeoutIdsRef.current.push(coveredId, revealId, doneId);
    }

    return {
        phase,
        direction,
        greeting,
        isPlaying: phase !== null,
        play,
    };
}
