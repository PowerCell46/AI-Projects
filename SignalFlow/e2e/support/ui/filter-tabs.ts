import type { Locator, Page } from '@playwright/test';
import { ENDPOINTS } from '../endpoints';
import { assertResponseOk, waitForFeedGet } from './network';

export type FilterView = 'All' | 'Subscribed' | 'Not subscribed';

const TAB_LIST_NAME = 'Topic filter';

function filterTab(page: Page, view: FilterView): Locator {
    const tabList = page.getByRole(
        'tablist',
        { name: TAB_LIST_NAME },
    );

    return tabList.getByRole(
        'tab',
        { name: new RegExp(`^${view}`) },
    );
}

// Same staleness as a bare subscription click, one level up: after a tab click React renders the
// previous list until the refetch resolves, so assertions can pass on pre-switch content. An
// already active tab triggers no refetch, so return immediately instead of waiting for one.
export async function selectFilterTab(page: Page, view: FilterView): Promise<void> {
    const tab = filterTab(page, view);
    const isSelected = await tab.getAttribute('aria-selected');

    if (isSelected === 'true') {
        return;
    }

    const feedResponse = waitForFeedGet(page);

    await tab.click();

    await assertResponseOk(feedResponse, `GET ${ENDPOINTS.feed}`);
}
