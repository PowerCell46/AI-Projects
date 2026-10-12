import { act, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fetchPeople, followUser } from '../../../api/users';
import type { PeoplePage, Person } from '../../../api/users';
import { FollowUpdatesProvider, useFollowUpdates } from '../../../contexts/FollowUpdatesContext';
import type { FollowUpdate } from '../../../contexts/FollowUpdatesContext';
import { reachListBottom } from '../../../test/postListHelpers';
import PeopleList from './PeopleList';


vi.mock('../../../api/users', () => ({
    fetchPeople: vi.fn(),
    followUser: vi.fn(),
    unfollowUser: vi.fn(),
}));

function person(username: string, changes: Partial<Person> = {}): Person {
    return {
        id: `id-${username}`,
        username,
        bio: `bio of ${username}`,
        followersCount: 3,
        followedByMe: false,
        profilePictureUrl: null,
        ...changes,
    };
}

function page(people: Person[], nextCursor: string | null): PeoplePage {
    return {
        items: people,
        nextCursor,
    };
}

function usernames(): string[] {
    return screen
        .queryAllByRole('listitem')
        .map((card) => card.querySelector('.person-card-name')?.textContent ?? '');
}

// The list's own region comes after the cards, whose follow buttons each carry one of their own.
function liveRegion(): HTMLElement {
    const region = Array
        .from(document.querySelectorAll<HTMLElement>('[aria-live="polite"]'))
        .at(-1);

    if (!region) {
        throw new Error('Expected the list live region.');
    }

    return region;
}

async function renderList(onFollowChanged = vi.fn(), extra: ReactNode = null) {
    await act(async () => {
        render(
            <MemoryRouter>
                <FollowUpdatesProvider>
                    {extra}
                    <PeopleList onFollowChanged={onFollowChanged} />
                </FollowUpdatesProvider>
            </MemoryRouter>,
        );
    });

    return { onFollowChanged };
}

beforeEach(() => {
    vi.mocked(fetchPeople).mockReset();
});

afterEach(() => {
    vi.useRealTimers();
});

describe('the first page', () => {
    it('should_ask_for_the_first_page_without_a_cursor_and_with_a_page_size_of_20', async () => {
        vi.mocked(fetchPeople).mockResolvedValue(page([], null));

        await renderList();

        expect(fetchPeople).toHaveBeenCalledExactlyOnceWith({
            cursor: null,
            size: 20,
        });
    });

    it('should_show_one_card_per_person_in_the_order_sent', async () => {
        vi.mocked(fetchPeople).mockResolvedValue(page(
            [person('ana'), person('bob'), person('cy')],
            null,
        ));

        await renderList();

        expect(usernames()).toEqual(['ana', 'bob', 'cy']);
    });

    it('should_list_the_cards_in_a_list', async () => {
        vi.mocked(fetchPeople).mockResolvedValue(page([person('ana')], null));

        await renderList();

        expect(screen.getByRole('list')).toBeTruthy();
    });
});

describe('the bottom of the list', () => {
    it('should_show_fetching_more_while_the_first_page_is_on_its_way', async () => {
        vi.mocked(fetchPeople).mockReturnValue(new Promise(() => undefined));

        await renderList();

        expect(screen.getByText('FETCHING MORE')).toBeTruthy();
    });

    it('should_end_with_end_of_people_when_there_is_no_cursor', async () => {
        vi.mocked(fetchPeople).mockResolvedValue(page([person('ana')], null));

        await renderList();

        expect(screen.getByText('END OF PEOPLE')).toBeTruthy();
    });

    it('should_say_no_one_else_here_yet_when_there_is_nobody_to_show', async () => {
        vi.mocked(fetchPeople).mockResolvedValue(page([], null));

        await renderList();

        expect(screen.getByText('NO ONE ELSE HERE YET')).toBeTruthy();
        expect(screen.queryByText('END OF PEOPLE')).toBeNull();
    });

    it('should_show_nothing_at_the_bottom_while_more_pages_are_waiting', async () => {
        vi.mocked(fetchPeople).mockResolvedValue(page([person('ana')], 'c1'));

        await renderList();

        expect(screen.queryByText('END OF PEOPLE')).toBeNull();
        expect(screen.queryByText('FETCHING MORE')).toBeNull();
        expect(screen.queryByText('SIGNAL LOST')).toBeNull();
    });

    it('should_say_signal_lost_with_a_try_again_button_when_the_page_cannot_be_read', async () => {
        vi.mocked(fetchPeople).mockRejectedValue(new Error('The request failed.'));

        await renderList();

        expect(screen.getByText('SIGNAL LOST')).toBeTruthy();
        expect(screen.getByRole('button', { name: 'TRY AGAIN' })).toBeTruthy();
    });

    it('should_ask_for_the_same_page_again_when_try_again_is_pressed', async () => {
        vi.mocked(fetchPeople)
            .mockResolvedValueOnce(page([person('ana')], 'c1'))
            .mockRejectedValueOnce(new Error('The request failed.'))
            .mockResolvedValueOnce(page([person('bob')], null));
        await renderList();
        await reachListBottom();

        await act(async () => {
            await userEvent.click(screen.getByRole('button', { name: 'TRY AGAIN' }));
        });

        expect(fetchPeople).toHaveBeenCalledTimes(3);
        expect(vi.mocked(fetchPeople).mock.calls[2][0]).toEqual({
            cursor: 'c1',
            size: 20,
        });
        expect(usernames()).toEqual(['ana', 'bob']);
    });
});

describe('the next pages', () => {
    it('should_load_the_next_page_with_the_cursor_when_the_bottom_comes_into_range', async () => {
        vi.mocked(fetchPeople)
            .mockResolvedValueOnce(page([person('ana')], 'c1'))
            .mockResolvedValueOnce(page([person('bob')], null));
        await renderList();

        await reachListBottom();

        expect(vi.mocked(fetchPeople).mock.calls[1][0]).toEqual({
            cursor: 'c1',
            size: 20,
        });
        expect(usernames()).toEqual(['ana', 'bob']);
    });

    it('should_not_load_anything_when_the_list_has_ended', async () => {
        vi.mocked(fetchPeople).mockResolvedValue(page([person('ana')], null));
        await renderList();

        await reachListBottom();

        expect(fetchPeople).toHaveBeenCalledTimes(1);
    });

    it('should_show_a_person_once_when_a_later_page_repeats_them', async () => {
        vi.mocked(fetchPeople)
            .mockResolvedValueOnce(page([person('ana'), person('bob')], 'c1'))
            .mockResolvedValueOnce(page([person('bob'), person('cy')], null));
        await renderList();

        await reachListBottom();

        expect(usernames()).toEqual(['ana', 'bob', 'cy']);
    });

    it('should_ask_for_the_following_page_at_once_when_a_page_adds_nobody_new', async () => {
        vi.mocked(fetchPeople)
            .mockResolvedValueOnce(page([person('ana')], 'c1'))
            .mockResolvedValueOnce(page([person('ana')], 'c2'))
            .mockResolvedValueOnce(page([person('bob')], null));
        await renderList();

        await reachListBottom();

        expect(fetchPeople).toHaveBeenCalledTimes(3);
        expect(usernames()).toEqual(['ana', 'bob']);
    });

    it('should_not_end_the_list_on_an_empty_page_that_has_a_cursor', async () => {
        vi.mocked(fetchPeople)
            .mockResolvedValueOnce(page([], 'c1'))
            .mockResolvedValueOnce(page([person('ana')], null));

        await renderList();

        expect(usernames()).toEqual(['ana']);
        expect(screen.queryByText('NO ONE ELSE HERE YET')).toBeNull();
    });
});

describe('the live region', () => {
    it('should_stay_empty_when_the_first_page_arrives', async () => {
        vi.mocked(fetchPeople).mockResolvedValue(page([person('ana'), person('bob')], 'c1'));

        await renderList();

        expect(liveRegion().textContent).toBe('');
    });

    it('should_announce_how_many_people_a_later_page_added', async () => {
        vi.mocked(fetchPeople)
            .mockResolvedValueOnce(page([person('ana')], 'c1'))
            .mockResolvedValueOnce(page([person('bob'), person('cy'), person('di')], null));
        await renderList();

        await reachListBottom();

        expect(liveRegion().textContent).toBe('3 more people loaded');
    });

    it('should_use_the_singular_when_a_later_page_added_one_person', async () => {
        vi.mocked(fetchPeople)
            .mockResolvedValueOnce(page([person('ana')], 'c1'))
            .mockResolvedValueOnce(page([person('bob')], null));
        await renderList();

        await reachListBottom();

        expect(liveRegion().textContent).toBe('1 more person loaded');
    });
});

describe('a follow change', () => {
    it('should_pass_the_follow_change_of_a_card_on_to_the_owner_of_the_list', async () => {
        vi.mocked(followUser).mockResolvedValue(undefined);
        vi.mocked(fetchPeople).mockResolvedValue(page([person('ana')], null));
        const { onFollowChanged } = await renderList();

        await act(async () => {
            await userEvent.click(screen.getByRole('button', { name: 'Follow ana' }));
        });

        expect(onFollowChanged).toHaveBeenCalledTimes(1);
    });
});

describe('a follow reported from a profile', () => {
    interface ReportButtonProps {
        update: FollowUpdate;
    }

    function ReportButton({ update }: ReportButtonProps) {
        const { reportFollowUpdate } = useFollowUpdates();

        return (
            <button
                type="button"
                onClick={() => reportFollowUpdate('ana', update)}
            >
                REPORT
            </button>
        );
    }

    function followersOf(card: HTMLElement): string {
        return card.querySelector('.person-card-followers')?.textContent ?? '';
    }

    it('should_show_the_card_as_following_with_the_new_count_when_a_profile_reported_a_follow', async () => {
        vi.mocked(fetchPeople).mockResolvedValue(page([person('ana')], null));

        await renderList(
            vi.fn(),
            <ReportButton
                update={{
                    followedByMe: true,
                    followersCount: 4,
                }}
            />,
        );

        await act(async () => {
            await userEvent.click(screen.getByRole('button', { name: 'REPORT' }));
        });

        const card = screen.getByRole('listitem');

        expect(within(card).getByRole('button', { name: 'Follow ana' }).getAttribute('aria-pressed')).toBe('true');
        expect(followersOf(card)).toBe('4 FOLLOWERS');
    });

    it('should_reset_the_cards_optimistic_follow_when_a_reported_update_replaces_it', async () => {
        vi.mocked(followUser).mockResolvedValue(undefined);
        vi.mocked(fetchPeople).mockResolvedValue(page([person('ana')], null));

        await renderList(
            vi.fn(),
            <ReportButton
                update={{
                    followedByMe: false,
                    followersCount: 9,
                }}
            />,
        );
        const card = screen.getByRole('listitem');
        const followButton = within(card).getByRole('button', { name: 'Follow ana' });

        await act(async () => {
            await userEvent.click(followButton);
        });

        expect(followButton.getAttribute('aria-pressed')).toBe('true');

        await act(async () => {
            await userEvent.click(screen.getByRole('button', { name: 'REPORT' }));
        });

        const resetCard = screen.getByRole('listitem');
        const resetFollowButton = within(resetCard).getByRole('button', { name: 'Follow ana' });

        expect(resetFollowButton.getAttribute('aria-pressed')).toBe('false');
        expect(followersOf(resetCard)).toBe('9 FOLLOWERS');
    });
});
