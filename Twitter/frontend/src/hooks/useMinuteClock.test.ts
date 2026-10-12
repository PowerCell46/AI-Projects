import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useMinuteClock } from './useMinuteClock';


const MINUTE_MS = 60_000;

function advance(milliseconds: number): void {
    act(() => {
        vi.advanceTimersByTime(milliseconds);
    });
}

beforeEach(() => {
    vi.useFakeTimers();
});

afterEach(() => {
    vi.useRealTimers();
});

describe('useMinuteClock', () => {
    it('should_move_the_time_forward_every_minute', () => {
        const { result } = renderHook(() => useMinuteClock());
        const startedAt = result.current.getTime();

        advance(MINUTE_MS);

        expect(result.current.getTime()).toBe(startedAt + MINUTE_MS);
    });

    it('should_freeze_the_time_while_hidden', () => {
        const { result } = renderHook(() => useMinuteClock(false));
        const startedAt = result.current.getTime();

        advance(MINUTE_MS * 5);

        expect(result.current.getTime()).toBe(startedAt);
    });

    it('should_tick_again_after_being_shown_again', () => {
        const { result, rerender } = renderHook(({ isShown }) => useMinuteClock(isShown), {
            initialProps: { isShown: true },
        });
        const startedAt = result.current.getTime();

        rerender({ isShown: false });
        advance(MINUTE_MS * 5);

        expect(result.current.getTime()).toBe(startedAt);

        rerender({ isShown: true });
        advance(MINUTE_MS);

        expect(result.current.getTime()).toBe(startedAt + MINUTE_MS * 6);
    });
});
