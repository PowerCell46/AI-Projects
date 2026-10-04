import { screen } from '@testing-library/react';
import { IntersectionObserverDouble, intersect } from './intersectionObserver';


// The list item holding the only post on screen, which is the element the view tracking watches.
export function onlyPostItem(): HTMLElement {
    const postItem = screen
        .getByRole('article')
        .closest('li');

    if (!postItem) {
        throw new Error('Expected the post to sit in a list item.');
    }

    return postItem;
}

// Mirrors the root margin useBottomSentinel passes to its IntersectionObserver; the double finds that observer by it.
const NEAR_BOTTOM_ROOT_MARGIN = '0px 0px 500px 0px';

// Scrolls the list to its bottom: the sentinel that loads the next page comes into range.
export async function reachListBottom(): Promise<void> {
    const sentinelObserver = IntersectionObserverDouble.active.find(
        (observer) => observer.rootMargin === NEAR_BOTTOM_ROOT_MARGIN,
    );
    const sentinel = Array.from(sentinelObserver?.targets ?? [])[0];

    if (!sentinel) {
        throw new Error('Expected the list to be watching its bottom sentinel.');
    }

    await intersect(sentinel);
}
