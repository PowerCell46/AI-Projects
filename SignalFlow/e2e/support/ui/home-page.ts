import { expect } from '@playwright/test';
import type { Locator, Page } from '@playwright/test';
import { ROUTES } from '../routes';
import { SELECTORS } from '../selectors';

export function wordmark(page: Page): Locator {
    return page.locator(SELECTORS.homeWordmark);
}

function feedHeading(page: Page): Locator {
    return page.getByRole(
        'heading',
        { name: 'Feed' },
    );
}

function signOutButton(page: Page): Locator {
    return page.getByRole(
        'button',
        { name: 'Sign out' },
    );
}

export async function signOut(page: Page): Promise<void> {
    await signOutButton(page).click();
}

export async function expectFeedPage(page: Page): Promise<void> {
    await expect(page).toHaveURL(ROUTES.home);
    await expect(feedHeading(page)).toBeVisible();
}
