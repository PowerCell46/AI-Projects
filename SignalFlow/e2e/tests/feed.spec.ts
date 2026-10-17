import { expect, test } from '@playwright/test';
import { registerNewUser } from '../support/api/auth-api';
import {
    createTopicViaApi,
    createTopicWithCategoriesViaApi,
    createTopicsBatchViaApi,
} from '../support/api/topic-scenarios';
import { FEED_PAGE_SIZE } from '../support/constants';
import { ROUTES } from '../support/routes';
import { feedRetryButton, feedState } from '../support/ui/feed-state';
import { collectViewTitles, revealTopicCard } from '../support/ui/feed-paging';
import { selectFilterTab } from '../support/ui/filter-tabs';
import { waitForFeedGet } from '../support/ui/network';
import { startFeedOutage } from '../support/ui/route-stubs';
import { loginAndWaitForFeed, loginViaUi } from '../support/ui/session';
import {
    clickSubscriptionToggle,
    topicCard,
    topicCategoryChips,
    topicSchedule,
    topicToggle,
} from '../support/ui/topic-card';

test('each filter view shows only its own topics', async ({ page, request }) => {
    const user = await registerNewUser(request);
    const followed = await createTopicViaApi();
    const unfollowed = await createTopicViaApi();

    await loginAndWaitForFeed(page, user);
    await revealTopicCard(page, followed.name);
    await clickSubscriptionToggle(page, followed.name);
    await expect(topicToggle(page, followed.name)).toHaveText('Subscribed');

    // The subscribed view is per-user and tiny, so direct assertions are
    // complete there; the global views below need full paging.
    await selectFilterTab(page, 'Subscribed');
    await expect(topicCard(page, followed.name)).toBeVisible();
    await expect(topicCard(page, unfollowed.name)).toHaveCount(0);

    await selectFilterTab(page, 'Not subscribed');
    const unsubscribedTitles = await collectViewTitles(page);
    expect(unsubscribedTitles).toContain(unfollowed.name);
    expect(unsubscribedTitles).not.toContain(followed.name);

    await selectFilterTab(page, 'All');
    const allTitles = await collectViewTitles(page);
    expect(allTitles).toContain(followed.name);
    expect(allTitles).toContain(unfollowed.name);
});

test('a topic with two categories shows both on its card, in name order', async ({ page, request }) => {
    const user = await registerNewUser(request);
    const topic = await createTopicWithCategoriesViaApi(['alpha', 'beta']);

    await loginAndWaitForFeed(page, user);
    await revealTopicCard(page, topic.name);

    await expect(topicCategoryChips(page, topic.name)).toHaveText(topic.categoryNames);
});

test('a topic with three categories shows two and a +1 chip naming the third', async ({ page, request }) => {
    const user = await registerNewUser(request);
    const topic = await createTopicWithCategoriesViaApi(['alpha', 'beta', 'gamma']);

    await loginAndWaitForFeed(page, user);
    await revealTopicCard(page, topic.name);

    const chips = topicCategoryChips(page, topic.name);
    const [firstCategory, secondCategory, thirdCategory] = topic.categoryNames;

    await expect(chips).toHaveText([firstCategory, secondCategory, '+1']);
    await expect(chips.last()).toHaveAttribute('title', thirdCategory);
});

test('a card shows its schedule, Daily by default', async ({ page, request }) => {
    const user = await registerNewUser(request);
    const weeklyTopic = await createTopicViaApi({ schedule: 'WEEKLY' });
    const defaultTopic = await createTopicViaApi();

    await loginAndWaitForFeed(page, user);
    await revealTopicCard(page, weeklyTopic.name);
    await expect(topicSchedule(page, weeklyTopic.name)).toHaveText('Weekly · Mondays');

    await revealTopicCard(page, defaultTopic.name);
    await expect(topicSchedule(page, defaultTopic.name)).toHaveText('Daily');
});

test('a fresh account sees the empty state in the subscribed view', async ({ page, request }) => {
    const user = await registerNewUser(request);

    await loginAndWaitForFeed(page, user);
    await selectFilterTab(page, 'Subscribed');

    await expect(feedState(page)).toHaveText(
        'No topics in this view. Switch to All to find something to follow.',
    );
});

test('pages through more topics than fit on one screen', async ({ page, request }) => {
    const user = await registerNewUser(request);

    // One topic past a full page forces at least one "load more".
    const createdTopics = await createTopicsBatchViaApi(FEED_PAGE_SIZE + 1);

    await loginAndWaitForFeed(page, user);

    const titles = await collectViewTitles(page);

    for (const createdTopic of createdTopics) {
        expect(titles).toContain(createdTopic.name);
    }
});

test('displays error state and recovers on retry when feed API fails', async ({ page, request }) => {
    const user = await registerNewUser(request);
    const topic = await createTopicViaApi();
    const feedOutage = await startFeedOutage(page);

    await loginViaUi(page, user);
    await expect(page).toHaveURL(ROUTES.home);

    await expect(feedState(page)).toContainText("Couldn't load the feed. Try again.");
    await expect(feedRetryButton(page)).toBeVisible();

    // End the outage before retrying, so the retry request reaches the real feed.
    const recoveredFeed = waitForFeedGet(page);
    feedOutage.restore();
    await feedRetryButton(page).click();

    const recoveredResponse = await recoveredFeed;
    expect(recoveredResponse.ok()).toBe(true);

    // The All view is shared across parallel tests and paged,
    // so our topic can land beyond page one — page until it is on screen.
    await revealTopicCard(page, topic.name);
    await expect(feedRetryButton(page)).toHaveCount(0);
});
