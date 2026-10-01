import { useEffect, useRef, useState } from 'react';
import { prefersReducedMotion } from '../../../../utils/motion';


export const DEPTH_COUNT_DURATION_MS = 950;

function easeOutCubic(progress: number): number {
    return 1 - (1 - progress) ** 3;
}

function animateDepthCount(
    startMetres: number,
    targetMetres: number,
    onFrame: (metres: number) => void,
): () => void {
    let animationFrameId = 0;
    let startTimestamp: number | null = null;

    function handleFrame(timestamp: number) {
        const firstTimestamp = startTimestamp ?? timestamp;
        const progress = Math.min(1, (timestamp - firstTimestamp) / DEPTH_COUNT_DURATION_MS);

        startTimestamp = firstTimestamp;
        onFrame(Math.round(startMetres + (targetMetres - startMetres) * easeOutCubic(progress)));

        if (progress < 1) {
            animationFrameId = requestAnimationFrame(handleFrame);
        }
    }

    animationFrameId = requestAnimationFrame(handleFrame);

    return () => cancelAnimationFrame(animationFrameId);
}

export function useDepthCount(targetMetres: number): number {
    const [displayedMetres, setDisplayedMetres] = useState(targetMetres);
    const displayedMetresRef = useRef(targetMetres);

    useEffect(() => {
        const startMetres = displayedMetresRef.current;

        function showMetres(metres: number) {
            displayedMetresRef.current = metres;
            setDisplayedMetres(metres);
        }

        if (startMetres === targetMetres) {
            return;
        }

        if (prefersReducedMotion()) {
            showMetres(targetMetres);

            return;
        }

        return animateDepthCount(startMetres, targetMetres, showMetres);
    }, [targetMetres]);

    return displayedMetres;
}
