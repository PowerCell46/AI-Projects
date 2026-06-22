import { useCallback, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useSweepClock } from '../../hooks/useSweepClock';
import { usePrefersReducedMotion } from '../../hooks/usePrefersReducedMotion';
import { APP_ROUTES } from '../../constants/routes';
import type { AuthMode } from '../../types/auth';


const SWEEP_DURATION_MS = 800;


const oppositeOf = (mode: AuthMode): AuthMode => {
    return mode === 'login' ? 'register' : 'login';
};

const routeOf = (mode: AuthMode): string => {
    return mode === 'login' ? APP_ROUTES.LOGIN : APP_ROUTES.REGISTER;
};


export interface ModeTransition {
    // The mode being revealed during a sweep, or null when idle.
    sweepTo: AuthMode | null;
    // Sweep clock, 0 -> 1.
    progress: number;
    // Begins a sweep to the opposite mode (no-op while one is in flight).
    switchMode: () => void;
}


/**
 * Drives the login <-> register VHS swap. The committed mode is owned by the URL (passed
 * in), so a swap reveals the target behind the bands and then navigates with replace,
 * keeping the address bar in step. Honors prefers-reduced-motion by navigating instantly.
 */
export const useModeTransition = (mode: AuthMode): ModeTransition => {
    const prefersReducedMotion = usePrefersReducedMotion();
    const navigate = useNavigate();

    const [pendingTarget, setPendingTarget] = useState<AuthMode | null>(null);

    // Promote the revealed mode by updating the URL; the base layer follows the new prop. Clearing
    // the pending target as we commit retires the overlay and keeps later sweeps from misreading a
    // leftover value.
    const commitTarget = useCallback(() => {
        if (pendingTarget !== null) {
            navigate(routeOf(pendingTarget), { replace: true });
            setPendingTarget(null);
        }
    }, [navigate, pendingTarget]);

    const { progress, isRunning, start } = useSweepClock(commitTarget);

    const switchMode = useCallback(() => {
        if (isRunning) {
            return;
        }

        const target = oppositeOf(mode);

        if (prefersReducedMotion) {
            navigate(routeOf(target), { replace: true });

            return;
        }

        setPendingTarget(target);
        start(SWEEP_DURATION_MS);
    }, [mode, isRunning, prefersReducedMotion, navigate, start]);

    // Reveal the overlay only until the URL (committed mode) catches up to the target,
    // at which point the base layer already shows it and the overlay can vanish.
    const sweepTo = pendingTarget !== null && pendingTarget !== mode ? pendingTarget : null;

    return { sweepTo, progress, switchMode };
};
