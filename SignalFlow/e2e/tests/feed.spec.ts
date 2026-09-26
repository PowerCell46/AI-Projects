import { expect, test } from '@playwright/test'
import {
    clickSubscriptionToggle,
    collectViewTitles,
    createTopicViaApi,
    createTopicsBatchViaApi,
    isFeedGet,
    loginAndWaitForFeed,
    loginViaUi,
    newUser,
    registerViaApi,
    revealTopicCard,
    selectFilterTab,
    topicCard,
} from './fixtures'

test('each filter view shows only its own topics', async ({ page, request }) => {
    const user = newUser()
    await registerViaApi(request, user)
    const followed = await createTopicViaApi(request)
    const unfollowed = await createTopicViaApi(request)

    await loginAndWaitForFeed(page, user)
    await revealTopicCard(page, followed.name)
    await clickSubscriptionToggle(page, followed.name)
    await expect(topicCard(page, followed.name).getByRole('button')).toHaveText('Subscribed')

    // The subscribed view is per-user and tiny, so direct assertions are
    // complete there; the global views below need full paging.
    await selectFilterTab(page, /^Subscribed/)
    await expect(topicCard(page, followed.name)).toBeVisible()
    await expect(topicCard(page, unfollowed.name)).toHaveCount(0)

    await selectFilterTab(page, /^Not subscribed/)
    const unsubscribedTitles = await collectViewTitles(page)
    expect(unsubscribedTitles).toContain(unfollowed.name)
    expect(unsubscribedTitles).not.toContain(followed.name)

    await selectFilterTab(page, /^All/)
    const allTitles = await collectViewTitles(page)
    expect(allTitles).toContain(followed.name)
    expect(allTitles).toContain(unfollowed.name)
})

test('a fresh account sees the empty state in the subscribed view', async ({
    page,
    request,
}) => {
    const user = newUser()
    await registerViaApi(request, user)

    await loginAndWaitForFeed(page, user)

    await selectFilterTab(page, /^Subscribed/)

    await expect(page.locator('.feed-state')).toHaveText(
        'No topics in this view. Switch to All to find something to follow.',
    )
})

test('pages through more topics than fit on one screen', async ({ page, request }) => {
    const user = newUser()
    await registerViaApi(request, user)

    // The feed loads 20 topics per page; 21 forces at least one "load more".
    // Batch create under 1 category and 1 admin session for efficiency.
    const createdTopics = await createTopicsBatchViaApi(21)
    const names = new Set(createdTopics.map((t) => t.name))

    await loginAndWaitForFeed(page, user)

    const titles = await collectViewTitles(page)
    for (const name of names) {
        expect(titles).toContain(name)
    }
})

test('displays error state and recovers on retry when feed API fails', async ({
    page,
    request,
}) => {
    const user = newUser()
    await registerViaApi(request, user)
    const topic = await createTopicViaApi(request)

    // Simulate feed API failure (500)
    let shouldFail = true
    await page.route('**/api/v1/feed*', async (route) => {
        if (shouldFail) {
            await route.fulfill({
                status: 500,
                contentType: 'application/json',
                body: JSON.stringify({ message: 'Internal Server Error' }),
            })
        } else {
            await route.continue()
        }
    })

    await loginViaUi(page, user)
    await expect(page).toHaveURL('/')

    const errorState = page.locator('.feed-state')
    await expect(errorState).toContainText("Couldn't load the feed. Try again.")
    const retryButton = page.locator('.feed-retry')
    await expect(retryButton).toBeVisible()

    // Resolve error and click Retry
    const recoveredFeed = page.waitForResponse(isFeedGet)
    shouldFail = false
    await retryButton.click()
    await recoveredFeed

    await expect(page.locator('.feed-retry')).toHaveCount(0)
    await expect(page.locator('.topic-card-title', { hasText: topic.name })).toBeVisible()
})
