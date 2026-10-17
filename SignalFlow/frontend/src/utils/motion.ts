export const REDUCED_MOTION_QUERY = '(prefers-reduced-motion: reduce)';

export function isReducedMotionPreferred(): boolean {
    return window.matchMedia(REDUCED_MOTION_QUERY).matches;
}
