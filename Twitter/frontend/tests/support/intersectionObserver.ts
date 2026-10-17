import { act } from '@testing-library/react';


// jsdom has no IntersectionObserver. This double records every observer a component creates and lets a test say
// when and how much a target is visible, so scrolling is simulated without layout.
export class IntersectionObserverDouble implements IntersectionObserver {
    static instances: IntersectionObserverDouble[] = [];

    readonly root: Element | Document | null;
    readonly rootMargin: string;
    readonly scrollMargin = '0px';
    readonly thresholds: readonly number[];
    readonly targets = new Set<Element>();
    isDisconnected = false;

    private readonly callback: IntersectionObserverCallback;

    constructor(callback: IntersectionObserverCallback, options: IntersectionObserverInit = {}) {
        this.callback = callback;
        this.root = options.root ?? null;
        this.rootMargin = options.rootMargin ?? '0px';
        this.thresholds = [options.threshold ?? 0].flat();

        IntersectionObserverDouble.instances.push(this);
    }

    static get active(): IntersectionObserverDouble[] {
        return IntersectionObserverDouble.instances.filter((observer) => !observer.isDisconnected);
    }

    // A disconnected observer can watch again, as in a browser.
    observe(target: Element): void {
        this.targets.add(target);
        this.isDisconnected = false;
    }

    unobserve(target: Element): void {
        this.targets.delete(target);
    }

    disconnect(): void {
        this.targets.clear();
        this.isDisconnected = true;
    }

    takeRecords(): IntersectionObserverEntry[] {
        return [];
    }

    report(target: Element, intersectionRatio: number): void {
        const bounds = target.getBoundingClientRect();
        const entry: IntersectionObserverEntry = {
            target,
            intersectionRatio,
            isIntersecting: intersectionRatio > 0,
            boundingClientRect: bounds,
            intersectionRect: bounds,
            rootBounds: null,
            time: 0,
        };

        this.callback([entry], this);
    }
}

export function resetIntersectionObservers(): void {
    IntersectionObserverDouble.instances = [];
}

// Tells every live observer watching the target how much of it is visible, inside act so React settles.
export async function intersect(target: Element, intersectionRatio = 1): Promise<void> {
    await act(async () => {
        IntersectionObserverDouble.active
            .filter((observer) => observer.targets.has(target))
            .forEach((observer) => observer.report(target, intersectionRatio));
    });
}

export function observersWatching(target: Element): IntersectionObserverDouble[] {
    return IntersectionObserverDouble.active.filter((observer) => observer.targets.has(target));
}
