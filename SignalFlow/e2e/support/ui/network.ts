import type { Page, Response } from '@playwright/test';
import { ENDPOINTS } from '../endpoints';

export function isFeedGet(response: Response): boolean {
    const url = response.url();
    const request = response.request();

    return url.includes(ENDPOINTS.feed) && request.method() === 'GET';
}

export async function waitForFeedGet(page: Page): Promise<Response> {
    return page.waitForResponse(isFeedGet);
}

// Fails with the HTTP status when the app itself errors, instead of timing out in a later assertion.
export async function assertResponseOk(
    responsePromise: Promise<Response>,
    requestLabel: string,
): Promise<void> {
    const response = await responsePromise;

    if (!response.ok()) {
        throw new Error(`${requestLabel} failed with ${response.status()}`);
    }
}
