import { ENDPOINTS } from './endpoints';
import { requestVoid } from './request';

export function subscribe(interestTopicId: string): Promise<void> {
    return requestVoid(
        ENDPOINTS.subscriptions,
        {
            method: 'POST',
            body: { interestTopicId },
        },
    );
}

export function unsubscribe(interestTopicId: string): Promise<void> {
    return requestVoid(
        ENDPOINTS.subscription(interestTopicId),
        { method: 'DELETE' },
    );
}
