import { expect } from '@playwright/test';
import type { Locator, Page } from '@playwright/test';
import { MAX_PAGER_LOADS } from '../constants';
import { SELECTORS } from '../selectors';
import { waitForViewSettled } from './feed-state';
import { topicCard, topicCards, topicTitles } from './topic-card';

function pagerControl(page: Page): Locator {
    return page.locator(SELECTORS.pagerControl);
}

async function hasMoreTopics(page: Page): Promise<boolean> {
    const pagerCount = await pagerControl(page).count();

    return pagerCount > 0;
}

async function loadNextPage(page: Page): Promise<void> {
    const countBefore = await topicCards(page).count();

    await pagerControl(page).click();

    await expect
        .poll(() => topicCards(page).count())
        .toBeGreaterThan(countBefore);
}

// Global views (ALL, NOT_SUBSCRIBED) are shared across parallel tests and sort
// alphabetically, so our topics can sit beyond page one. Page to exhaustion
// and return every shown title; callers assert inclusion on the result.
export async function collectViewTitles(page: Page): Promise<string[]> {
    await waitForViewSettled(page);

    for (let loadCount = 0; loadCount < MAX_PAGER_LOADS; loadCount++) {
        if (!(await hasMoreTopics(page))) {
            break;
        }

        await loadNextPage(page);
    }

    return topicTitles(page);
}

// Same paging, but stops once our card is on screen — for toggling a topic
// that may start beyond page one.
export async function revealTopicCard(page: Page, name: string): Promise<void> {
    await waitForViewSettled(page);

    for (let loadCount = 0; loadCount < MAX_PAGER_LOADS; loadCount++) {
        const cardCount = await topicCard(page, name).count();

        if (cardCount > 0 || !(await hasMoreTopics(page))) {
            break;
        }

        await loadNextPage(page);
    }

    await expect(topicCard(page, name)).toBeVisible();
}
