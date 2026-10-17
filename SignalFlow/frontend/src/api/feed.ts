import { ENDPOINTS } from './endpoints';
import { requestJson } from './request';

export type FeedFilterValue = 'ALL' | 'SUBSCRIBED' | 'NOT_SUBSCRIBED';

export type FeedSchedule = 'DAILY' | 'WEEKDAYS' | 'WEEKENDS' | 'WEEKLY';

export interface FeedCategory {
    id: string;
    name: string;
}

export interface FeedTopic {
    id: string;
    name: string;
    description: string;
    categories: FeedCategory[];
    schedule: FeedSchedule;
    subscribed: boolean;
}

export interface FeedCounts {
    all: number;
    subscribed: number;
    notSubscribed: number;
}

export interface FeedPage {
    items: FeedTopic[];
    nextCursor: string | null;
    counts: FeedCounts;
}

export function fetchFeed(filter: FeedFilterValue, after: string | null, size: number): Promise<FeedPage> {
    const params = new URLSearchParams({
        filter,
        size: String(size),
    });

    if (after) {
        params.set('after', after);
    }

    return requestJson<FeedPage>(`${ENDPOINTS.feed}?${params.toString()}`);
}
