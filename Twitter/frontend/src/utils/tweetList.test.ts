import { describe, expect, it } from 'vitest';
import type { TweetItem } from '../api/tweetPage';
import { appendUnique, findNewerItems, mergeNewestFirst, prependUnique } from './tweetList';


function item(id: string, createdAt = '2026-10-04T10:00:00Z'): TweetItem {
    return {
        id,
        views: 0,
        savedByMe: false,
        likes: 0,
        likedByMe: false,
        content: `tweet ${id}`,
        createdAt,
        updatedAt: createdAt,
        author: {
            id: 'author-1',
            username: 'ana',
            profilePictureUrl: null,
        },
        images: [],
    };
}

function idsOf(items: TweetItem[]): string[] {
    return items.map((tweet) => tweet.id);
}

describe('appendUnique', () => {
    it('should_add_the_incoming_items_after_the_current_ones_in_order', () => {
        const merged = appendUnique([item('a'), item('b')], [item('c'), item('d')]);

        expect(idsOf(merged)).toEqual(['a', 'b', 'c', 'd']);
    });

    it('should_drop_an_incoming_item_that_is_already_in_the_list', () => {
        const merged = appendUnique([item('a'), item('b')], [item('b'), item('c')]);

        expect(idsOf(merged)).toEqual(['a', 'b', 'c']);
    });

    it('should_keep_the_item_already_shown_when_an_incoming_one_has_the_same_id', () => {
        const shown = {
            ...item('a'),
            savedByMe: true,
            likes: 0,
            likedByMe: false,
        };

        const merged = appendUnique([shown], [item('a')]);

        expect(merged).toEqual([shown]);
    });

    it('should_return_the_current_items_when_nothing_is_incoming', () => {
        expect(idsOf(appendUnique([item('a')], []))).toEqual(['a']);
    });

    it('should_return_the_incoming_items_when_the_list_is_empty', () => {
        expect(idsOf(appendUnique([], [item('a'), item('b')]))).toEqual(['a', 'b']);
    });

    it('should_not_change_the_lists_it_was_given', () => {
        const current = [item('a')];
        const incoming = [item('b')];

        appendUnique(current, incoming);

        expect(idsOf(current)).toEqual(['a']);
        expect(idsOf(incoming)).toEqual(['b']);
    });
});

describe('prependUnique', () => {
    it('should_put_the_incoming_items_before_the_current_ones_in_order', () => {
        const merged = prependUnique([item('c'), item('d')], [item('a'), item('b')]);

        expect(idsOf(merged)).toEqual(['a', 'b', 'c', 'd']);
    });

    it('should_drop_an_incoming_item_that_is_already_in_the_list', () => {
        const merged = prependUnique([item('b'), item('c')], [item('a'), item('b')]);

        expect(idsOf(merged)).toEqual(['a', 'b', 'c']);
    });

    it('should_return_the_current_items_when_every_incoming_item_is_already_shown', () => {
        expect(idsOf(prependUnique([item('a')], [item('a')]))).toEqual(['a']);
    });
});

function findNew(firstPage: TweetItem[], current: TweetItem[]): TweetItem[] {
    return findNewerItems(firstPage, current[0], current);
}

describe('findNewerItems', () => {
    const TOP = item('m', '2026-10-04T10:00:00.500Z');

    it('should_return_the_items_created_after_the_top_post', () => {
        const newer = item('x', '2026-10-04T10:00:01Z');
        const older = item('y', '2026-10-04T09:59:59Z');

        expect(findNew([newer, TOP, older], [TOP])).toEqual([newer]);
    });

    it('should_return_nothing_when_the_page_holds_only_what_is_on_screen', () => {
        expect(findNew([TOP], [TOP])).toEqual([]);
    });

    it('should_return_nothing_when_the_first_page_is_empty', () => {
        expect(findNew([], [TOP])).toEqual([]);
    });

    it('should_order_an_item_with_the_same_time_by_the_higher_id_first', () => {
        const higherId = item('z', TOP.createdAt);
        const lowerId = item('a', TOP.createdAt);

        expect(findNew([higherId, TOP, lowerId], [TOP])).toEqual([higherId]);
    });

    it('should_not_count_an_item_that_is_already_shown_further_down_the_list', () => {
        const shownBelow = item('x', '2026-10-04T10:00:01Z');
        const below = item('b', '2026-10-04T09:00:00Z');

        expect(findNew([shownBelow], [TOP, below, shownBelow])).toEqual([]);
    });

    it('should_compare_timestamps_with_different_numbers_of_fraction_digits', () => {
        const wholeSecond = item('x', '2026-10-04T10:00:01Z');
        const microseconds = item('y', '2026-10-04T10:00:00.500001Z');
        const sameTimeLowerId = item('a', '2026-10-04T10:00:00.5Z');
        const justBefore = item('v', '2026-10-04T10:00:00.499999Z');

        const found = findNew([wholeSecond, microseconds, sameTimeLowerId, justBefore], [TOP]);

        expect(idsOf(found)).toEqual(['x', 'y']);
    });

    it('should_treat_every_item_of_the_page_as_new_when_nothing_is_on_screen_yet', () => {
        const first = item('a');
        const second = item('b');

        expect(findNew([first, second], [])).toEqual([first, second]);
    });

    it('should_compare_with_the_newest_loaded_post_and_not_with_the_own_posts_shown_above_it', () => {
        const mine = item('mine', '2026-10-04T14:00:00.000Z');
        const betweenTheTwo = item('x', '2026-10-04T12:00:00.000Z');

        expect(findNewerItems([betweenTheTwo, TOP], TOP, [mine, TOP])).toEqual([betweenTheTwo]);
    });

    it('should_not_count_an_own_post_that_the_server_has_delivered_too', () => {
        const mine = item('mine', '2026-10-04T14:00:00.000Z');

        expect(findNewerItems([mine, TOP], TOP, [mine, TOP])).toEqual([]);
    });

    it('should_keep_the_order_of_the_page', () => {
        const newest = item('x', '2026-10-04T10:00:03Z');
        const middle = item('y', '2026-10-04T10:00:02Z');

        expect(idsOf(findNew([newest, middle, TOP], [TOP]))).toEqual(['x', 'y']);
    });
});

describe('mergeNewestFirst', () => {
    it('should_put_an_extra_post_above_the_base_when_it_is_newer_than_all_of_it', () => {
        const merged = mergeNewestFirst(
            [item('b', '2026-10-04T10:00:00Z')],
            [item('a', '2026-10-04T11:00:00Z')],
        );

        expect(idsOf(merged)).toEqual(['a', 'b']);
    });

    it('should_slot_an_extra_post_between_base_posts_by_time', () => {
        const merged = mergeNewestFirst(
            [item('new', '2026-10-04T12:00:00Z'), item('old', '2026-10-04T09:00:00Z')],
            [item('mine', '2026-10-04T10:00:00Z')],
        );

        expect(idsOf(merged)).toEqual(['new', 'mine', 'old']);
    });

    it('should_put_an_extra_post_below_the_base_when_it_is_older_than_all_of_it', () => {
        const merged = mergeNewestFirst(
            [item('a', '2026-10-04T11:00:00Z')],
            [item('b', '2026-10-04T10:00:00Z')],
        );

        expect(idsOf(merged)).toEqual(['a', 'b']);
    });

    it('should_order_posts_with_the_same_time_by_the_higher_id_first', () => {
        const merged = mergeNewestFirst([item('a')], [item('z')]);

        expect(idsOf(merged)).toEqual(['z', 'a']);
    });

    it('should_drop_an_extra_post_whose_id_is_already_in_the_base', () => {
        const inBase = {
            ...item('same'),
            savedByMe: true,
            likes: 0,
            likedByMe: false,
        };

        const merged = mergeNewestFirst([inBase], [item('same')]);

        expect(merged).toEqual([inBase]);
    });

    it('should_keep_several_extra_posts_in_the_order_they_were_given', () => {
        const merged = mergeNewestFirst(
            [item('base', '2026-10-04T08:00:00Z')],
            [item('second', '2026-10-04T11:00:00Z'), item('first', '2026-10-04T10:00:00Z')],
        );

        expect(idsOf(merged)).toEqual(['second', 'first', 'base']);
    });

    it('should_return_the_base_when_there_is_nothing_extra', () => {
        expect(idsOf(mergeNewestFirst([item('a'), item('b')], []))).toEqual(['a', 'b']);
    });

    it('should_return_the_extra_posts_when_the_base_is_empty', () => {
        expect(idsOf(mergeNewestFirst([], [item('a'), item('b')]))).toEqual(['a', 'b']);
    });

    it('should_not_change_the_lists_it_was_given', () => {
        const base = [item('a')];
        const extra = [item('b')];

        mergeNewestFirst(base, extra);

        expect(idsOf(base)).toEqual(['a']);
        expect(idsOf(extra)).toEqual(['b']);
    });
});
