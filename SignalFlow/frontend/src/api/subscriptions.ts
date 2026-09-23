const BASE_URL = import.meta.env.VITE_BASE_API_URL ?? ''

export class SubscriptionApiError extends Error {
    readonly status: number

    constructor(status: number) {
        super(`Subscription request failed with status ${status}`)
        this.status = status
    }
}

export async function subscribe(interestTopicId: string): Promise<void> {
    const response = await fetch(`${BASE_URL}/api/v1/subscriptions`, {
        method: 'POST',
        credentials: 'include',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({ interestTopicId }),
    })

    if (!response.ok) {
        throw new SubscriptionApiError(response.status)
    }
}

export async function unsubscribe(interestTopicId: string): Promise<void> {
    const response = await fetch(`${BASE_URL}/api/v1/subscriptions/${interestTopicId}`, {
        method: 'DELETE',
        credentials: 'include',
    })

    if (!response.ok) {
        throw new SubscriptionApiError(response.status)
    }
}
