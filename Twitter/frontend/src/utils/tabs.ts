import { matchPath } from 'react-router-dom';
import { ROUTES } from '../routes';


export type TabId = 'tweets' | 'people';

export interface TabDefinition {
    id: TabId;
    label: string;
    path: string;
}

export const TABS: TabDefinition[] = [
    {
        id: 'tweets',
        label: 'TWEETS',
        path: ROUTES.feed,
    },
    {
        id: 'people',
        label: 'PEOPLE',
        path: ROUTES.people,
    },
];

export const DEFAULT_TAB: TabId = 'tweets';

export function tabOfPath(pathname: string): TabId | null {
    const matchedTab = TABS.find((tab) => matchPath(tab.path, pathname) !== null);

    return matchedTab?.id ?? null;
}

export function tabButtonId(tab: TabId): string {
    return `tab-${tab}`;
}

export function tabPanelId(tab: TabId): string {
    return `tab-panel-${tab}`;
}
