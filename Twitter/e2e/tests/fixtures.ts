import { randomUUID } from 'node:crypto'
import { expect, type APIRequestContext, type Page } from '@playwright/test'
import pg from 'pg'

export interface TestUser {
    email: string
    username: string
    password: string
}

const DATABASE_URL = 'postgresql://twitter_e2e:twitter_e2e@localhost:5452/twitter_e2e'

export const TEST_PASSWORD = 'Passw0rd-e2e'

// Usernames are 3-15 letters, digits or underscores, so "e2e_" plus 8 hex characters.
export function newUser(): TestUser {
    const suffix = randomUUID().replaceAll('-', '').slice(0, 8)

    return {
        email: `e2e_${suffix}@example.com`,
        username: `e2e_${suffix}`,
        password: TEST_PASSWORD,
    }
}

async function queryConfirmationUrls(email: string): Promise<string[]> {
    const client = new pg.Client({ connectionString: DATABASE_URL })

    await client.connect()

    try {
        const result = await client.query<{ confirmation_url: string }>(
            `select payload::jsonb->>'confirmationUrl' as confirmation_url
             from outbox
             where payload::jsonb->>'email' = $1
             order by created_at`,
            [email],
        )

        return result.rows.map((row) => row.confirmation_url)

    } finally {
        await client.end()
    }
}

// The newest confirmation link the gateway queued for this email. Waits until a row has been written.
export async function latestConfirmationUrl(email: string, minimumRowCount = 1): Promise<string> {
    let urls: string[] = []

    await expect.poll(async () => {
        urls = await queryConfirmationUrls(email)

        return urls.length
    }).toBeGreaterThanOrEqual(minimumRowCount)

    return urls[urls.length - 1]
}

export async function confirmationRowCount(email: string): Promise<number> {
    return (await queryConfirmationUrls(email)).length
}

export async function registerViaApi(request: APIRequestContext, user: TestUser) {
    const response = await request.post('/api/v1/auth/register', { data: user })

    expect(response.ok()).toBe(true)
}

export async function registerAndConfirmViaApi(request: APIRequestContext, user: TestUser) {
    await registerViaApi(request, user)

    const confirmationUrl = new URL(await latestConfirmationUrl(user.email))
    const token = confirmationUrl.searchParams.get('token')
    const response = await request.post('/api/v1/auth/confirm', { data: { token } })

    expect(response.ok()).toBe(true)
}

// Each step advances on Enter. A password input is not a textbox, so it is found by its label.
// The form ignores Enter while a step is still sliding in, so the step must be at rest before it is pressed.
export async function typeStep(page: Page, label: string, value: string) {
    await page.getByLabel(label, { exact: true }).fill(value)

    await expect(page.locator('.step-flow-block')).toHaveAttribute('data-slide', 'idle')

    await page.getByLabel(label, { exact: true }).press('Enter')
}

export async function logIn(page: Page, identifier: string, password: string) {
    await page.goto('/login')
    await typeStep(page, 'email or username', identifier)
    await typeStep(page, 'password', password)
}

export async function logInAndWaitForFeed(page: Page, user: TestUser) {
    await logIn(page, user.username, user.password)

    await expect(page).toHaveURL(/\/feed$/)
}
