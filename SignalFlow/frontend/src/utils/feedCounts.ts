import type { FeedCounts, FeedFilterValue } from '../api/feed';

export function countForFilter(counts: FeedCounts, filter: FeedFilterValue): number {
    if (filter === 'ALL') {
        return counts.all;
    }

    if (filter === 'SUBSCRIBED') {
        return counts.subscribed;
    }

    return counts.notSubscribed;
}
