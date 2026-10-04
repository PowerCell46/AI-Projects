import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DEPTH_COUNT_DURATION_MS, useDepthCount } from './useDepthCount';


const FRAME_MS = 16;

function stubReducedMotion(isReduced: boolean): void {
    vi.stubGlobal(
        'matchMedia',
        () => ({ matches: isReduced }),
    );
}

function advance(milliseconds: number): void {
    act(() => {
        vi.advanceTimersByTime(milliseconds);
    });
}

beforeEach(() => {
    vi.useFakeTimers({ toFake: ['requestAnimationFrame', 'cancelAnimationFrame', 'performance'] });
    stubReducedMotion(false);
});

afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
});

describe('useDepthCount', () => {
    it('should_start_at_the_target_without_counting', () => {
        const { result } = renderHook(() => useDepthCount(140));

        expect(result.current).toBe(140);
    });

    it('should_count_up_and_land_exactly_on_the_target', () => {
        const { result, rerender } = renderHook(({ target }) => useDepthCount(target), {
            initialProps: { target: 140 },
        });

        rerender({ target: 3860 });
        advance(FRAME_MS * 3);

        expect(result.current).toBeGreaterThan(140);
        expect(result.current).toBeLessThan(3860);

        advance(DEPTH_COUNT_DURATION_MS + FRAME_MS * 2);

        expect(result.current).toBe(3860);
    });

    it('should_count_down_and_land_exactly_on_the_target', () => {
        const { result, rerender } = renderHook(({ target }) => useDepthCount(target), {
            initialProps: { target: 4900 },
        });

        rerender({ target: 4480 });
        advance(FRAME_MS * 3);

        expect(result.current).toBeLessThan(4900);
        expect(result.current).toBeGreaterThan(4480);

        advance(DEPTH_COUNT_DURATION_MS + FRAME_MS * 2);

        expect(result.current).toBe(4480);
    });

    it('should_ease_out_so_more_than_half_the_distance_is_covered_by_the_midpoint', () => {
        const { result, rerender } = renderHook(({ target }) => useDepthCount(target), {
            initialProps: { target: 0 },
        });

        rerender({ target: 1000 });
        advance(FRAME_MS + DEPTH_COUNT_DURATION_MS / 2);

        expect(result.current).toBeGreaterThan(800);
    });

    it('should_jump_straight_to_the_target_when_reduced_motion_is_requested', () => {
        stubReducedMotion(true);
        const { result, rerender } = renderHook(({ target }) => useDepthCount(target), {
            initialProps: { target: 140 },
        });

        rerender({ target: 3860 });

        expect(result.current).toBe(3860);
    });

    it('should_restart_from_the_shown_value_when_the_target_changes_mid_count', () => {
        const { result, rerender } = renderHook(({ target }) => useDepthCount(target), {
            initialProps: { target: 0 },
        });

        rerender({ target: 1000 });
        advance(FRAME_MS * 10);
        const shownMetres = result.current;
        rerender({ target: 0 });
        advance(FRAME_MS * 2);

        expect(result.current).toBeLessThanOrEqual(shownMetres);

        advance(DEPTH_COUNT_DURATION_MS + FRAME_MS * 2);

        expect(result.current).toBe(0);
    });
});
