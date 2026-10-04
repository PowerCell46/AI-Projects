import { describe, expect, it } from 'vitest';
import { ROUTES } from '../routes';
import { DEFAULT_TAB, TABS, tabButtonId, tabOfPath, tabPanelId } from './tabs';


describe('tabOfPath', () => {
    it.each([
        [ROUTES.feed, 'tweets'],
        [ROUTES.people, 'people'],
        [`${ROUTES.feed}/`, 'tweets'],
        [`${ROUTES.people}/`, 'people'],
    ])('should_map_%s_to_the_%s_tab', (pathname, expectedTab) => {
        expect(tabOfPath(pathname)).toBe(expectedTab);
    });

    it.each([
        ROUTES.saved,
        ROUTES.login,
        '/',
        '/feed/extra',
        '/users/bob',
    ])('should_map_%s_to_no_tab', (pathname) => {
        expect(tabOfPath(pathname)).toBeNull();
    });
});

describe('TABS', () => {
    it('should_list_tweets_before_people_with_their_labels_and_paths', () => {
        expect(TABS).toEqual([
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
        ]);
    });

    it('should_make_tweets_the_default_tab', () => {
        expect(DEFAULT_TAB).toBe('tweets');
    });
});

describe('tab ids', () => {
    it('should_give_every_tab_a_button_id_and_a_panel_id_that_differ', () => {
        const ids = TABS.flatMap((tab) => [tabButtonId(tab.id), tabPanelId(tab.id)]);

        expect(new Set(ids).size).toBe(TABS.length * 2);
    });
});
