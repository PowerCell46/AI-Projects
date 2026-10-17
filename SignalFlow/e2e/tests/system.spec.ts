import { expect, test } from '@playwright/test';
import { clearMailpitMessages, getMailpitInfo, getMailpitMessages } from '../support/api/mailpit-api';
import { ENDPOINTS } from '../support/endpoints';
import { HTTP_STATUS } from '../support/http-status';

const UNKNOWN_TOPIC_ID = '00000000-0000-0000-0000-000000000000';

test.describe('system & service health', () => {
    test('mailpit is reachable and message inbox can be queried and cleared', async () => {
        const info = await getMailpitInfo();
        expect(info).not.toBeNull();
        expect(info).toHaveProperty('Version');

        await clearMailpitMessages();

        const messages = await getMailpitMessages();
        expect(messages).toEqual([]);
    });

    test('protected API routes reject unauthenticated requests with 401', async ({ request }) => {
        const feedResponse = await request.get(ENDPOINTS.feed);
        expect(feedResponse.status()).toBe(HTTP_STATUS.unauthorized);

        const subscriptionResponse = await request.post(ENDPOINTS.subscription(UNKNOWN_TOPIC_ID));
        expect(subscriptionResponse.status()).toBe(HTTP_STATUS.unauthorized);
    });

    test('non-admin user cannot create categories or topics directly', async ({ request }) => {
        const categoryResponse = await request.post(
            ENDPOINTS.categories,
            { data: { name: 'unauthorized-category' } },
        );

        expect([HTTP_STATUS.unauthorized, HTTP_STATUS.forbidden]).toContain(categoryResponse.status());
    });
});
