import type { Locator, Page, Response } from '@playwright/test';
import { ENDPOINTS } from '../endpoints';
import { SELECTORS } from '../selectors';
import { assertResponseOk } from './network';

const SUBSCRIBE_LABEL = 'Subscribe';

export function topicCards(page: Page): Locator {
    return page.locator(SELECTORS.topicCard);
}

export function topicCard(page: Page, name: string): Locator {
    const title = page.locator(
        SELECTORS.topicCardTitle,
        { hasText: name },
    );

    return page.locator(
        SELECTORS.topicCard,
        { has: title },
    );
}

export function topicToggle(page: Page, name: string): Locator {
    return topicCard(page, name).getByRole('button');
}

export function topicCategoryChips(page: Page, name: string): Locator {
    return topicCard(page, name).locator(SELECTORS.topicCardCategory);
}

export function topicSchedule(page: Page, name: string): Locator {
    return topicCard(page, name).locator(SELECTORS.topicCardSchedule);
}

export async function topicTitles(page: Page): Promise<string[]> {
    const titles = page.locator(SELECTORS.topicCardTitle);

    return titles.allTextContents();
}

function isSubscriptionResponse(response: Response, method: string): boolean {
    const url = response.url();
    const request = response.request();

    return url.includes(ENDPOINTS.subscriptions) && request.method() === method;
}

// The card flips its label optimistically, before the POST/DELETE commits —
// assertions right after a bare click can pass on UI the server hasn't caught
// up with yet. Waiting for the round-trip makes every later fetch genuine.
export async function clickSubscriptionToggle(page: Page, name: string): Promise<void> {
    const toggle = topicToggle(page, name);
    const label = await toggle.textContent();
    const method = (label ?? '').trim() === SUBSCRIBE_LABEL ? 'POST' : 'DELETE';

    const subscriptionResponse = page.waitForResponse((response) => isSubscriptionResponse(response, method));

    await toggle.click();

    await assertResponseOk(subscriptionResponse, `${method} ${ENDPOINTS.subscriptions}`);
}
