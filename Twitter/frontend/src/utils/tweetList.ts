import type { Identified } from '../api/paging';
import type { TweetItem } from '../api/tweetPage';


const FRACTION_DIGITS = 9;

interface SplitTimestamp {
    seconds: string;
    fraction: string;
}

function splitTimestamp(timestamp: string): SplitTimestamp {
    const [seconds, fraction = ''] = timestamp
        .replace(/Z$/, '')
        .split('.');

    return {
        seconds,
        fraction: fraction.padEnd(FRACTION_DIGITS, '0'),
    };
}

// The backend writes 0, 3, 6 or 9 fraction digits, so the strings can't be compared as they are.
function compareTimestamps(first: string, second: string): number {
    const firstParts = splitTimestamp(first);
    const secondParts = splitTimestamp(second);

    if (firstParts.seconds !== secondParts.seconds) {
        return firstParts.seconds < secondParts.seconds ? -1 : 1;
    }

    if (firstParts.fraction === secondParts.fraction) {
        return 0;
    }

    return firstParts.fraction < secondParts.fraction ? -1 : 1;
}

// The feed's keyset order: newest first, ties broken by the higher id.
function isOrderedBefore(candidate: TweetItem, other: TweetItem): boolean {
    const byTime = compareTimestamps(candidate.createdAt, other.createdAt);

    return byTime !== 0 ? byTime > 0 : candidate.id > other.id;
}

function withoutIds<T extends Identified>(items: T[], excludedIds: Set<string>): T[] {
    return items.filter((item) => !excludedIds.has(item.id));
}

function idsOf(items: Identified[]): Set<string> {
    return new Set(items.map((item) => item.id));
}

export function appendUnique<T extends Identified>(current: T[], incoming: T[]): T[] {
    return [...current, ...withoutIds(incoming, idsOf(current))];
}

export function prependUnique<T extends Identified>(current: T[], incoming: T[]): T[] {
    return [...withoutIds(incoming, idsOf(current)), ...current];
}

// Both lists must already be newest first. A post of `extra` whose id is in `base` is dropped; the others are slotted
// in by the feed's order, so my own post lands below a newer post of someone else and above an older one.
export function mergeNewestFirst(base: TweetItem[], extra: TweetItem[]): TweetItem[] {
    const additions = withoutIds(extra, idsOf(base));
    const merged: TweetItem[] = [];
    let baseIndex = 0;
    let additionIndex = 0;

    while (baseIndex < base.length || additionIndex < additions.length) {
        const isAdditionNext = baseIndex >= base.length
            || (additionIndex < additions.length && isOrderedBefore(additions[additionIndex], base[baseIndex]));

        merged.push(isAdditionNext ? additions[additionIndex++] : base[baseIndex++]);
    }

    return merged;
}

// Items of a freshly read first page that belong above the newest loaded post and are not on screen yet. `shownItems`
// may hold more than the loaded ones (the reader's own new posts), which is why the top is passed on its own.
export function findNewerItems(
    firstPage: TweetItem[],
    newestLoadedItem: TweetItem | undefined,
    shownItems: TweetItem[],
): TweetItem[] {
    const shownIds = idsOf(shownItems);

    return firstPage.filter((tweet) => {
        const isShown = shownIds.has(tweet.id);
        const isAboveNewestLoaded = !newestLoadedItem || isOrderedBefore(tweet, newestLoadedItem);

        return !isShown && isAboveNewestLoaded;
    });
}
