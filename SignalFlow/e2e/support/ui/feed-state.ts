import { expect } from '@playwright/test';
import type { Locator, Page } from '@playwright/test';
import { SELECTORS } from '../selectors';

export function feedState(page: Page): Locator {
    return page.locator(SELECTORS.feedState);
}

export function feedRetryButton(page: Page): Locator {
    return page.locator(SELECTORS.feedRetry);
}

// The loading marker is painted before any fetch response can arrive, so its
// absence proves the latest fetch rendered — the only trustworthy signal that
// pager presence below reflects current (not previous-view) state.
export async function waitForViewSettled(page: Page): Promise<void> {
    const loadingMarker = page.locator(
        SELECTORS.feedState,
        { hasText: 'Loading' },
    );

    await expect(loadingMarker).toHaveCount(0);
}
