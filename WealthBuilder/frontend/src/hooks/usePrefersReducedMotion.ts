import { useEffect, useState } from 'react';


const QUERY = '(prefers-reduced-motion: reduce)';


/**
 * Tracks the user's reduced-motion preference, updating live if it changes. The VHS
 * sweep reads this to fall back to an instant mode swap.
 */
export const usePrefersReducedMotion = (): boolean => {
    const [prefersReduced, setPrefersReduced] = useState<boolean>(
        () => window.matchMedia(QUERY).matches,
    );

    useEffect(() => {
        const mediaQuery = window.matchMedia(QUERY);

        const handleChange = (event: MediaQueryListEvent): void => {
            setPrefersReduced(event.matches);
        };

        mediaQuery.addEventListener('change', handleChange);

        return () => {
            mediaQuery.removeEventListener('change', handleChange);
        };
    }, []);

    return prefersReduced;
};
