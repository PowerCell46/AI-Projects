import { randomUUID } from 'node:crypto'
import { expect, request as playwrightRequest } from '@playwright/test'
import type { APIRequestContext, Locator, Page } from '@playwright/test'

// Satisfies the gateway's password rules (8-72 chars, upper + lower + digit).
export const TEST_PASSWORD = 'Password123'

// Seeded by the gateway's DatabaseLoader from ADMIN_EMAIL/ADMIN_PASSWORD in
// docker-compose.e2e.yml. Must also satisfy the login DTO rules.
const ADMIN_EMAIL = 'admin@e2e.local'
const ADMIN_PASSWORD = 'Admin12345'

export interface TestUser {
    email: string
    password: string
}

export interface TestTopic {
    categoryId: string
    topicId: string
    name: string
}

export interface MailpitMessage {
    ID: string
    From: { Name: string; Address: string }
    To: { Name: string; Address: string }[]
    Subject: string
    Created: string
}

export function newUser(): TestUser {
    return { email: `e2e-${randomUUID()}@example.com`, password: TEST_PASSWORD }
}

async function postOrThrow(
    request: APIRequestContext,
    path: string,
    data: unknown,
): Promise<Record<string, unknown>> {
    const response = await request.post(path, { data })

    if (!response.ok()) {
        const body = await response.text()
        throw new Error(`POST ${path} failed with ${response.status()}: ${body}`)
    }

    return (await response.json()) as Record<string, unknown>
}

export async function registerViaApi(request: APIRequestContext, user: TestUser): Promise<void> {
    await postOrThrow(request, '/api/v1/auth/register', user)
}

/**
 * Creates an isolated APIRequestContext for admin operations.
 * This guarantees that admin session cookies never contaminate a test user's `request` fixture.
 */
export async function getAdminApiContext(baseURL = 'http://localhost:8090'): Promise<APIRequestContext> {
    const adminContext = await playwrightRequest.newContext({ baseURL })
    await postOrThrow(adminContext, '/api/v1/auth/login', {
        email: ADMIN_EMAIL,
        password: ADMIN_PASSWORD,
    })
    return adminContext
}

// Logs in as the seeded admin and creates one category + one topic through the
// gateway's forwarded routes (writes are ADMIN-only). Names are unique per
// call so parallel tests never see each other's topics by accident.
export async function createTopicViaApi(
    _request?: APIRequestContext,
    name?: string,
): Promise<TestTopic> {
    const adminReq = await getAdminApiContext()
    try {
        const category = await postOrThrow(adminReq, '/api/v1/categories', {
            name: `e2e-category-${randomUUID()}`,
        })

        const topicName = name ?? `e2e-topic-${randomUUID()}`
        const topic = await postOrThrow(adminReq, '/api/v1/interest-topics', {
            name: topicName,
            description: `e2e description for ${topicName}`,
            prompt: 'e2e prompt',
            categoryId: category['id'],
        })

        return {
            categoryId: category['id'] as string,
            topicId: topic['id'] as string,
            name: topicName,
        }
    } finally {
        await adminReq.dispose()
    }
}

/**
 * Efficiently creates multiple topics under a single category using a single admin session.
 * Eliminates repeated admin logins and excess categories.
 */
export async function createTopicsBatchViaApi(
    count: number,
    baseName = 'e2e-topic',
): Promise<TestTopic[]> {
    const adminReq = await getAdminApiContext()
    try {
        const category = await postOrThrow(adminReq, '/api/v1/categories', {
            name: `e2e-category-${randomUUID()}`,
        })
        const categoryId = category['id'] as string
        const created: TestTopic[] = []

        for (let i = 0; i < count; i++) {
            const topicName = `${baseName}-${i}-${randomUUID()}`
            const topic = await postOrThrow(adminReq, '/api/v1/interest-topics', {
                name: topicName,
                description: `e2e description for ${topicName}`,
                prompt: 'e2e prompt',
                categoryId,
            })
            created.push({
                categoryId,
                topicId: topic['id'] as string,
                name: topicName,
            })
        }

        return created
    } finally {
        await adminReq.dispose()
    }
}

// The auth page renders both forms with the inactive one inert + aria-hidden;
// scope to the visible form so selectors never match the hidden twin.
export function activeAuthForm(page: Page) {
    return page.locator('.auth-form-wrap[aria-hidden="false"]')
}

export async function loginViaUi(page: Page, user: TestUser): Promise<void> {
    await page.goto('/login')

    const form = activeAuthForm(page)
    await form.getByLabel('Email', { exact: true }).fill(user.email)
    await form.getByLabel('Password', { exact: true }).fill(user.password)
    await form.getByRole('button', { name: 'Sign in', exact: true }).click()
}

export function isFeedGet(response: { url(): string; request(): { method(): string } }): boolean {
    return response.url().includes('/api/v1/feed') && response.request().method() === 'GET'
}

// The login page issues no feed requests, so a waiter registered up front
// catches exactly the home page mount's initial fetch — proving it resolved
// no matter whether the feed came back empty or not. Without this, the first
// pager/tab check can run while the initial load is still in flight.
export async function loginAndWaitForFeed(page: Page, user: TestUser): Promise<void> {
    const initialFeed = page.waitForResponse(isFeedGet)
    await loginViaUi(page, user)
    await expect(page).toHaveURL('/')

    const response = await initialFeed
    if (!response.ok()) {
        throw new Error(`GET /api/v1/feed failed with ${response.status()}`)
    }
}

export function topicCard(page: Page, name: string): Locator {
    return page.locator('.topic-card', {
        has: page.locator('.topic-card-title', { hasText: name }),
    })
}

// The card flips its label optimistically, before the POST/DELETE commits —
// assertions right after a bare click can pass on UI the server hasn't caught
// up with yet. Waiting for the round-trip makes every later fetch genuine.
export async function clickSubscriptionToggle(page: Page, name: string): Promise<void> {
    const toggle = topicCard(page, name).getByRole('button')
    const method = ((await toggle.textContent()) ?? '').trim() === 'Subscribe' ? 'POST' : 'DELETE'

    const responsePromise = page.waitForResponse(
        (response) =>
            response.url().includes('/api/v1/subscriptions') &&
            response.request().method() === method,
    )
    await toggle.click()

    const response = await responsePromise
    if (!response.ok()) {
        throw new Error(`${method} /api/v1/subscriptions failed with ${response.status()}`)
    }
}

// The loading marker is painted before any fetch response can arrive, so its
// absence proves the latest fetch rendered — the only trustworthy signal that
// pager presence below reflects current (not previous-view) state.
async function waitForViewSettled(page: Page): Promise<void> {
    await expect(page.locator('.feed-state', { hasText: 'Loading' })).toHaveCount(0)
}

// Global views (ALL, NOT_SUBSCRIBED) are shared across parallel tests and sort
// alphabetically, so our topics can sit beyond page one. Page to exhaustion
// and return every shown title; callers assert inclusion on the result.
export async function collectViewTitles(page: Page): Promise<string[]> {
    await waitForViewSettled(page)

    const pagerButton = page.locator('.pager-control')
    let maxLoads = 100
    while (maxLoads-- > 0 && (await pagerButton.count()) > 0) {
        const before = await page.locator('.topic-card').count()
        await pagerButton.click()
        await expect
            .poll(async () => page.locator('.topic-card').count())
            .toBeGreaterThan(before)
    }

    return page.locator('.topic-card-title').allTextContents()
}

// Same paging, but stops once our card is on screen — for toggling a topic
// that may start beyond page one.
export async function revealTopicCard(page: Page, name: string): Promise<void> {
    await waitForViewSettled(page)

    const card = topicCard(page, name)
    const pagerButton = page.locator('.pager-control')
    let maxLoads = 100
    while (maxLoads-- > 0) {
        if ((await card.count()) > 0) {
            return
        }
        if ((await pagerButton.count()) === 0) {
            break
        }

        const before = await page.locator('.topic-card').count()
        await pagerButton.click()
        await expect
            .poll(async () => page.locator('.topic-card').count())
            .toBeGreaterThan(before)
    }
    await expect(card).toBeVisible()
}

// Same staleness one level up: after a tab click React renders the previous
// list until the refetch resolves, so bare tab clicks let assertions pass on
// pre-switch content. If tab is already active, return immediately to avoid
// hanging on a non-existent network response.
export async function selectFilterTab(page: Page, name: RegExp): Promise<void> {
    const tabs = page.getByRole('tablist', { name: 'Topic filter' })
    const targetTab = tabs.getByRole('tab', { name })

    const isSelected = await targetTab.getAttribute('aria-selected')
    if (isSelected === 'true') {
        return
    }

    const responsePromise = page.waitForResponse(isFeedGet)
    await targetTab.click()

    const response = await responsePromise
    if (!response.ok()) {
        throw new Error(`GET /api/v1/feed failed with ${response.status()}`)
    }
}

// Mailpit HTTP API helpers for notification email integration
export async function clearMailpitMessages(baseURL = 'http://localhost:8125'): Promise<void> {
    const ctx = await playwrightRequest.newContext({ baseURL })
    try {
        await ctx.delete('/api/v1/messages')
    } catch {
        // If mailpit is temporarily unreachable, ignore
    } finally {
        await ctx.dispose()
    }
}

export async function getMailpitMessages(baseURL = 'http://localhost:8125'): Promise<MailpitMessage[]> {
    const ctx = await playwrightRequest.newContext({ baseURL })
    try {
        const res = await ctx.get('/api/v1/messages')
        if (!res.ok()) return []
        const data = await res.json()
        return (data.messages ?? []) as MailpitMessage[]
    } catch {
        return []
    } finally {
        await ctx.dispose()
    }
}

export async function getMailpitInfo(baseURL = 'http://localhost:8125'): Promise<Record<string, unknown> | null> {
    const ctx = await playwrightRequest.newContext({ baseURL })
    try {
        const res = await ctx.get('/api/v1/info')
        if (!res.ok()) return null
        return (await res.json()) as Record<string, unknown>
    } catch {
        return null
    } finally {
        await ctx.dispose()
    }
}
