import { useCallback, useEffect, useRef } from 'react';
import { viewReporter } from '../../../utils/viewReporter';
import type { ViewReporter } from '../../../utils/viewReporter';


const MIN_VISIBLE_RATIO = 0.5;

const DWELL_MS = 1000;

type TrackView = (element: HTMLElement | null) => (() => void) | undefined;

// A post counts as viewed once at least half of it has stayed on screen for a second. Put the returned ref on the
// element of every post, with the post's id in `data-tweet-id`.
export function useViewTracking(reporter: ViewReporter = viewReporter): TrackView {
    const observerRef = useRef<IntersectionObserver | null>(null);
    const dwellTimerIdsRef = useRef(new Map<Element, number>());

    useEffect(() => {
        function flushWhenHidden() {
            if (document.visibilityState === 'hidden') {
                reporter.flush({ isKeepalive: true });
            }
        }

        function flushOnPageHide() {
            reporter.flush({ isKeepalive: true });
        }

        document.addEventListener('visibilitychange', flushWhenHidden);
        window.addEventListener('pagehide', flushOnPageHide);

        return () => {
            document.removeEventListener('visibilitychange', flushWhenHidden);
            window.removeEventListener('pagehide', flushOnPageHide);
        };
    }, [reporter]);

    return useCallback((element) => {
        if (!element) {
            return undefined;
        }

        function cancelDwell(target: Element) {
            window.clearTimeout(dwellTimerIdsRef.current.get(target));
            dwellTimerIdsRef.current.delete(target);
        }

        function startDwell(target: HTMLElement) {
            const tweetId = target.dataset.tweetId;

            if (!tweetId || dwellTimerIdsRef.current.has(target) || reporter.hasReported(tweetId)) {
                return;
            }

            const timerId = window.setTimeout(() => {
                dwellTimerIdsRef.current.delete(target);
                reporter.record(tweetId);
            }, DWELL_MS);

            dwellTimerIdsRef.current.set(target, timerId);
        }

        function handleEntries(entries: IntersectionObserverEntry[]) {
            entries.forEach((entry) => {
                if (!(entry.target instanceof HTMLElement)) {
                    return;
                }

                if (entry.intersectionRatio >= MIN_VISIBLE_RATIO) {
                    startDwell(entry.target);

                } else {
                    cancelDwell(entry.target);
                }
            });
        }

        const observer = observerRef.current ?? new IntersectionObserver(
            handleEntries,
            { threshold: [MIN_VISIBLE_RATIO] },
        );

        observerRef.current = observer;
        observer.observe(element);

        return () => {
            observer.unobserve(element);
            cancelDwell(element);
        };
    }, [reporter]);
}
