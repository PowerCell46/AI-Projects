import { act } from '@testing-library/react';
import { vi } from 'vitest';
import { STEP_SWAP_MS } from '../components/shared/StepFlow/useStepTransition';
import { depthPositionPercent } from '../utils/depth';


const SETTLE_MS = 100;

const HISTORY_TRAVERSAL_MS = 1;

export async function advance(milliseconds: number) {
    await act(async () => {
        await vi.advanceTimersByTimeAsync(milliseconds);
    });
}

// jsdom queues history traversal on the global setTimeout, so it needs the fake clock to move.
export async function waitForHistoryTraversal() {
    await advance(HISTORY_TRAVERSAL_MS);
}

// Two stages: act() holds back the state updates a timer makes until the act call ends, so the effect
// that schedules the release after the swap only starts once the first stage is over.
export async function settleTransition() {
    await advance(STEP_SWAP_MS);
    await advance(SETTLE_MS);
}

export function horizonTransform(): string {
    return document.querySelector<HTMLElement>('.horizon')?.style.transform ?? '';
}

export function expectedTransform(depthMetres: number, seafloorDepthMetres: number): string {
    return `translateY(${depthPositionPercent(depthMetres, seafloorDepthMetres)}%)`;
}
