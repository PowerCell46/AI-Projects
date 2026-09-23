const BASE_URL = import.meta.env.VITE_BASE_API_URL ?? ''

export type FeedFilter = 'ALL' | 'SUBSCRIBED' | 'NOT_SUBSCRIBED'

export interface FeedTopic {
    id: string
    name: string
    description: string
    categoryId: string
    categoryName: string
    subscribed: boolean
}

export interface FeedCounts {
    all: number
    subscribed: number
    notSubscribed: number
}

export interface FeedPage {
    items: FeedTopic[]
    nextCursor: string | null
    counts: FeedCounts
}

export class FeedApiError extends Error {
    readonly status: number

    constructor(status: number) {
        super(`Feed request failed with status ${status}`)
        this.status = status
    }
}

export async function fetchFeed(filter: FeedFilter, after: string | null, size: number): Promise<FeedPage> {
    const params = new URLSearchParams({
        filter,
        size: String(size),
    })

    if (after) {
        params.set('after', after)
    }

    const response = await fetch(`${BASE_URL}/api/v1/feed?${params.toString()}`, {
        credentials: 'include',
    })

    if (!response.ok) {
        throw new FeedApiError(response.status)
    }

    return response.json() as Promise<FeedPage>
}
