import type { Page, Route } from '@playwright/test';
import { ENDPOINTS } from '../endpoints';
import { HTTP_STATUS } from '../http-status';

export interface FeedOutage {
    restore(): void;
}

async function fulfillWithError(route: Route, status: number, message: string): Promise<void> {
    await route.fulfill({
        status,
        contentType: 'application/json',
        body: JSON.stringify({ message }),
    });
}

// Simulates an expired or invalidated session cookie: the app's auth check fails on the next load.
export async function stubSessionExpired(page: Page): Promise<void> {
    await page.route(
        `**${ENDPOINTS.auth.me}`,
        (route) => fulfillWithError(route, HTTP_STATUS.unauthorized, 'Unauthorized'),
    );
}

export async function startFeedOutage(page: Page): Promise<FeedOutage> {
    let isFailing = true;

    await page.route(
        `**${ENDPOINTS.feed}*`,
        async (route) => {
            if (isFailing) {
                await fulfillWithError(route, HTTP_STATUS.internalServerError, 'Internal Server Error');

            } else {
                await route.continue();
            }
        },
    );

    function restore(): void {
        isFailing = false;
    }

    return { restore };
}
