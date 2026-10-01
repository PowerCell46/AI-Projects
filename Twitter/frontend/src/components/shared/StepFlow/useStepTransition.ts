import { useEffect, useState } from 'react';
import { prefersReducedMotion } from '../../../utils/motion';


export type StepSlide = 'idle' | 'exit' | 'enter';

export type StepDirection = 'forward' | 'back';

export interface StepTransition {
    stepIndex: number;
    slide: StepSlide;
    direction: StepDirection;
}

// Must match the 400ms opacity fade in StepFlow.css, or the content swaps while it is still visible.
export const STEP_SWAP_MS = 400;

// One frame or two, so the browser paints the offset position before the step is released to rest.
const ENTER_RELEASE_MS = 30;

export function useStepTransition(requestedStepIndex: number): StepTransition {
    const [stepIndex, setStepIndex] = useState(0);
    const [isEntering, setIsEntering] = useState(false);
    const [enterDirection, setEnterDirection] = useState<StepDirection>('forward');

    const isExiting = requestedStepIndex !== stepIndex;

    useEffect(() => {
        if (!isExiting) {
            return;
        }

        const swapTimerId = window.setTimeout(
            () => {
                setEnterDirection(requestedStepIndex > stepIndex ? 'forward' : 'back');
                setStepIndex(requestedStepIndex);
                setIsEntering(true);
            },
            prefersReducedMotion() ? 0 : STEP_SWAP_MS,
        );

        return () => window.clearTimeout(swapTimerId);
    }, [isExiting, requestedStepIndex, stepIndex]);

    useEffect(() => {
        if (!isEntering) {
            return;
        }

        const releaseTimerId = window.setTimeout(() => setIsEntering(false), ENTER_RELEASE_MS);

        return () => window.clearTimeout(releaseTimerId);
    }, [isEntering]);

    const exitDirection: StepDirection = requestedStepIndex > stepIndex ? 'forward' : 'back';

    return {
        stepIndex,
        slide: isExiting ? 'exit' : isEntering ? 'enter' : 'idle',
        direction: isExiting ? exitDirection : enterDirection,
    };
}
