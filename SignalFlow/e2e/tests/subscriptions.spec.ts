import { expect, test } from '@playwright/test'
import {
    clickSubscriptionToggle,
    collectViewTitles,
    createTopicViaApi,
    isFeedGet,
    loginAndWaitForFeed,
    newUser,
    registerViaApi,
    revealTopicCard,
    selectFilterTab,
    topicCard,
} from './fixtures'

test('subscribes and unsubscribes through the topic card', async ({ page, request }) => {
    const user = newUser()
    await registerViaApi(request, user)
    const topic = await createTopicViaApi(request)

    await loginAndWaitForFeed(page, user)
    await revealTopicCard(page, topic.name)

    const toggle = topicCard(page, topic.name).getByRole('button')
    await expect(toggle).toHaveText('Subscribe')
    await clickSubscriptionToggle(page, topic.name)
    await expect(toggle).toHaveText('Subscribed')
    await expect(toggle).toHaveAttribute('aria-pressed', 'true')
    await clickSubscriptionToggle(page, topic.name)
    await expect(toggle).toHaveText('Subscribe')
    await expect(toggle).toHaveAttribute('aria-pressed', 'false')
})

test('a subscription moves the topic between filter views', async ({ page, request }) => {
    const user = newUser()
    await registerViaApi(request, user)
    const topic = await createTopicViaApi(request)

    await loginAndWaitForFeed(page, user)

    await selectFilterTab(page, /^Not subscribed/)
    expect(await collectViewTitles(page)).toContain(topic.name)

    await selectFilterTab(page, /^All/)
    await revealTopicCard(page, topic.name)
    await clickSubscriptionToggle(page, topic.name)
    await expect(topicCard(page, topic.name).getByRole('button')).toHaveText('Subscribed')

    await selectFilterTab(page, /^Subscribed/)
    await expect(topicCard(page, topic.name)).toBeVisible()

    await selectFilterTab(page, /^Not subscribed/)
    expect(await collectViewTitles(page)).not.toContain(topic.name)
})

test('subscription state persists across page reload', async ({ page, request }) => {
    const user = newUser()
    await registerViaApi(request, user)
    const topic = await createTopicViaApi(request)

    await loginAndWaitForFeed(page, user)
    await revealTopicCard(page, topic.name)
    await clickSubscriptionToggle(page, topic.name)
    await expect(topicCard(page, topic.name).getByRole('button')).toHaveText('Subscribed')

    // Reload the page and ensure the server returns the subscribed state
    const feedResponse = page.waitForResponse(isFeedGet)
    await page.reload()
    await feedResponse

    await selectFilterTab(page, /^Subscribed/)
    await expect(topicCard(page, topic.name)).toBeVisible()
    await expect(topicCard(page, topic.name).getByRole('button')).toHaveText('Subscribed')
})

test('subscription state persists across logout and re-login', async ({ page, request }) => {
    const user = newUser()
    await registerViaApi(request, user)
    const topic = await createTopicViaApi(request)

    await loginAndWaitForFeed(page, user)
    await revealTopicCard(page, topic.name)
    await clickSubscriptionToggle(page, topic.name)
    await expect(topicCard(page, topic.name).getByRole('button')).toHaveText('Subscribed')

    // Logout
    await page.getByRole('button', { name: 'Sign out' }).click()
    await expect(page).toHaveURL('/login')

    // Log back in
    await loginAndWaitForFeed(page, user)
    await selectFilterTab(page, /^Subscribed/)
    await expect(topicCard(page, topic.name)).toBeVisible()
    await expect(topicCard(page, topic.name).getByRole('button')).toHaveText('Subscribed')
})

