import { expect } from '@playwright/test';
import type { Page } from '@playwright/test';
import type { TestUser } from '../data/test-user';
import { ENDPOINTS } from '../endpoints';
import { ROUTES } from '../routes';
import { submitAuthForm } from './auth-form';
import { assertResponseOk, waitForFeedGet } from './network';

export async function loginViaUi(page: Page, user: TestUser): Promise<void> {
    await submitAuthForm(page, 'login', user.email, user.password);
}

// The login page issues no feed requests, so a waiter registered up front
// catches exactly the home page mount's initial fetch — proving it resolved
// no matter whether the feed came back empty or not. Without this, the first
// pager/tab check can run while the initial load is still in flight.
export async function loginAndWaitForFeed(page: Page, user: TestUser): Promise<void> {
    const initialFeed = waitForFeedGet(page);

    await loginViaUi(page, user);
    await expect(page).toHaveURL(ROUTES.home);

    await assertResponseOk(initialFeed, `GET ${ENDPOINTS.feed}`);
}
