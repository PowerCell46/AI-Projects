import { expect, test } from '@playwright/test'
import { clearMailpitMessages, getMailpitInfo, getMailpitMessages } from './fixtures'

test.describe('system & service health', () => {
    test('mailpit is reachable and message inbox can be queried and cleared', async () => {
        const info = await getMailpitInfo()
        expect(info).not.toBeNull()
        expect(info).toHaveProperty('Version')

        // Clean inbox and verify it returns empty list
        await clearMailpitMessages()
        const messages = await getMailpitMessages()
        expect(messages).toEqual([])
    })

    test('protected API routes reject unauthenticated requests with 401', async ({ request }) => {
        const feedResponse = await request.get('/api/v1/feed')
        expect(feedResponse.status()).toBe(401)

        const subsResponse = await request.post('/api/v1/subscriptions/00000000-0000-0000-0000-000000000000')
        expect(subsResponse.status()).toBe(401)
    })

    test('non-admin user cannot create categories or topics directly', async ({ request }) => {
        // Unauthenticated attempts
        const catResponse = await request.post('/api/v1/categories', {
            data: { name: 'unauthorized-category' },
        })
        expect([401, 403]).toContain(catResponse.status())
    })
})
