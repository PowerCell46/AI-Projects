import { expect, test } from '@playwright/test';
import { registerNewUser } from '../support/api/auth-api';
import { createTopicViaApi } from '../support/api/topic-scenarios';
import { ROUTES } from '../support/routes';
import { collectViewTitles, revealTopicCard } from '../support/ui/feed-paging';
import { selectFilterTab } from '../support/ui/filter-tabs';
import { waitForFeedGet } from '../support/ui/network';
import { signOut } from '../support/ui/home-page';
import { loginAndWaitForFeed } from '../support/ui/session';
import { clickSubscriptionToggle, topicCard, topicToggle } from '../support/ui/topic-card';

test('subscribes and unsubscribes through the topic card', async ({ page, request }) => {
    const user = await registerNewUser(request);
    const topic = await createTopicViaApi();

    await loginAndWaitForFeed(page, user);
    await revealTopicCard(page, topic.name);

    const toggle = topicToggle(page, topic.name);
    await expect(toggle).toHaveText('Subscribe');

    await clickSubscriptionToggle(page, topic.name);
    await expect(toggle).toHaveText('Subscribed');
    await expect(toggle).toHaveAttribute('aria-pressed', 'true');

    await clickSubscriptionToggle(page, topic.name);
    await expect(toggle).toHaveText('Subscribe');
    await expect(toggle).toHaveAttribute('aria-pressed', 'false');
});

test('a subscription moves the topic between filter views', async ({ page, request }) => {
    const user = await registerNewUser(request);
    const topic = await createTopicViaApi();

    await loginAndWaitForFeed(page, user);

    await selectFilterTab(page, 'Not subscribed');
    expect(await collectViewTitles(page)).toContain(topic.name);

    await selectFilterTab(page, 'All');
    await revealTopicCard(page, topic.name);
    await clickSubscriptionToggle(page, topic.name);
    await expect(topicToggle(page, topic.name)).toHaveText('Subscribed');

    await selectFilterTab(page, 'Subscribed');
    await expect(topicCard(page, topic.name)).toBeVisible();

    await selectFilterTab(page, 'Not subscribed');
    expect(await collectViewTitles(page)).not.toContain(topic.name);
});

test('subscription state persists across page reload', async ({ page, request }) => {
    const user = await registerNewUser(request);
    const topic = await createTopicViaApi();

    await loginAndWaitForFeed(page, user);
    await revealTopicCard(page, topic.name);
    await clickSubscriptionToggle(page, topic.name);
    await expect(topicToggle(page, topic.name)).toHaveText('Subscribed');

    // The server, not client state, must return the subscribed flag after a reload.
    const feedResponse = waitForFeedGet(page);
    await page.reload();
    await feedResponse;

    await selectFilterTab(page, 'Subscribed');
    await expect(topicCard(page, topic.name)).toBeVisible();
    await expect(topicToggle(page, topic.name)).toHaveText('Subscribed');
});

test('subscription state persists across logout and re-login', async ({ page, request }) => {
    const user = await registerNewUser(request);
    const topic = await createTopicViaApi();

    await loginAndWaitForFeed(page, user);
    await revealTopicCard(page, topic.name);
    await clickSubscriptionToggle(page, topic.name);
    await expect(topicToggle(page, topic.name)).toHaveText('Subscribed');

    await signOut(page);
    await expect(page).toHaveURL(ROUTES.login);

    await loginAndWaitForFeed(page, user);
    await selectFilterTab(page, 'Subscribed');
    await expect(topicCard(page, topic.name)).toBeVisible();
    await expect(topicToggle(page, topic.name)).toHaveText('Subscribed');
});
