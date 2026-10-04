import { act, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { followUser, unfollowUser } from '../../../../api/users';
import type { Person } from '../../../../api/users';
import PersonCard from './PersonCard';


vi.mock('../../../../api/users', () => ({
    followUser: vi.fn(),
    unfollowUser: vi.fn(),
}));

const FAILURE_RESET_MS = 3000;

const PERSON: Person = {
    id: '6f1c2a3e-0000-4000-8000-0000000000aa',
    username: 'ana',
    bio: 'Writes about the sea',
    followersCount: 1240,
    followedByMe: false,
    profilePictureUrl: null,
};

const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });

function renderCard(changes: Partial<Person> = {}, onFollowChanged = vi.fn()) {
    render(
        <ul>
            <PersonCard person={{ ...PERSON, ...changes }} onFollowChanged={onFollowChanged} />
        </ul>,
    );

    return { onFollowChanged };
}

function followButton(): HTMLElement {
    return screen.getByRole('button', { name: 'Follow ana' });
}

function visibleLabel(): string {
    return followButton().textContent ?? '';
}

function cardOf(): HTMLElement {
    return screen.getByRole('listitem');
}

async function advance(milliseconds: number) {
    await act(async () => {
        await vi.advanceTimersByTimeAsync(milliseconds);
    });
}

// A request that stays in flight until the test settles it.
function holdRequest(request: typeof followUser) {
    let resolveRequest = () => {};
    let rejectRequest = () => {};

    const pendingRequest = new Promise<void>((resolve, reject) => {
        resolveRequest = resolve;
        rejectRequest = () => reject(new Error('The request failed.'));
    });

    vi.mocked(request).mockReturnValueOnce(pendingRequest);

    return {
        resolve: () => act(async () => resolveRequest()),
        reject: () => act(async () => rejectRequest()),
    };
}

beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'requestAnimationFrame', 'cancelAnimationFrame'] });
    vi.mocked(followUser)
        .mockReset()
        .mockResolvedValue(undefined);
    vi.mocked(unfollowUser)
        .mockReset()
        .mockResolvedValue(undefined);
});

afterEach(() => {
    vi.useRealTimers();
});

describe('person card', () => {
    it('should_show_the_username_once_and_no_handle', () => {
        renderCard();

        expect(within(cardOf()).getAllByText('ana')).toHaveLength(1);
        expect(screen.queryByText('@ana')).toBeNull();
    });

    it('should_show_the_default_picture_in_the_large_avatar_when_there_is_no_picture', () => {
        renderCard();

        const avatar = cardOf().querySelector('img.avatar');

        expect(avatar?.getAttribute('src')).toBe('/Default-Profile-Picture.png');
        expect(avatar?.getAttribute('data-size')).toBe('large');
    });

    it('should_show_the_bio_as_it_is_when_it_fits', () => {
        renderCard();

        expect(screen.getByText('Writes about the sea')).toBeTruthy();
    });

    it('should_cut_a_long_bio_at_the_last_whole_word_and_add_an_ellipsis', () => {
        const longBio = 'I write long letters about the sea, the tide and the small boats that rest in the harbour';

        renderCard({ bio: longBio });

        expect(screen.getByText('I write long letters about the sea, the tide and…')).toBeTruthy();
    });

    it('should_keep_an_empty_bio_line_marked_as_empty_when_there_is_no_bio', () => {
        const { container } = render(
            <ul>
                <PersonCard person={{ ...PERSON, bio: null }} onFollowChanged={vi.fn()} />
            </ul>,
        );

        const bioLine = container.querySelector('.person-card-bio');

        expect(bioLine?.getAttribute('data-empty')).toBe('true');
        expect(bioLine?.textContent).toBe('');
    });

    it('should_not_mark_the_bio_line_as_empty_when_there_is_a_bio', () => {
        const { container } = render(
            <ul>
                <PersonCard person={PERSON} onFollowChanged={vi.fn()} />
            </ul>,
        );

        expect(container.querySelector('.person-card-bio')?.getAttribute('data-empty')).toBe('false');
    });

    it('should_show_the_follower_count_in_short_form_with_followers_after_it', () => {
        renderCard();

        expect(screen.getByText('1.2K FOLLOWERS')).toBeTruthy();
    });

    it('should_say_follower_in_the_singular_when_there_is_exactly_one', () => {
        renderCard({ followersCount: 1 });

        expect(screen.getByText('1 FOLLOWER')).toBeTruthy();
    });

    it('should_say_followers_when_there_are_none', () => {
        renderCard({ followersCount: 0 });

        expect(screen.getByText('0 FOLLOWERS')).toBeTruthy();
    });
});

describe('follow button at rest', () => {
    it('should_say_follow_and_not_be_pressed_when_the_person_is_not_followed', () => {
        renderCard();

        expect(visibleLabel()).toBe('FOLLOW');
        expect(followButton().getAttribute('aria-pressed')).toBe('false');
    });

    it('should_say_following_and_be_pressed_when_the_person_is_followed', () => {
        renderCard({ followedByMe: true });

        expect(visibleLabel()).toBe('FOLLOWING');
        expect(followButton().getAttribute('aria-pressed')).toBe('true');
    });

    it('should_keep_the_accessible_name_and_hide_the_visible_label_from_assistive_technology', () => {
        renderCard();

        expect(followButton().getAttribute('aria-label')).toBe('Follow ana');
        expect(followButton().querySelector('[aria-hidden="true"]')?.textContent).toBe('FOLLOW');
    });

    it('should_have_type_button', () => {
        renderCard();

        expect(followButton().getAttribute('type')).toBe('button');
    });
});

describe('following', () => {
    it('should_flip_the_button_and_the_count_at_once_and_ask_the_server_to_follow', async () => {
        const request = holdRequest(followUser);
        renderCard();

        await user.click(followButton());

        expect(visibleLabel()).toBe('FOLLOWING');
        expect(followButton().getAttribute('aria-pressed')).toBe('true');
        expect(screen.getByText('1.2K FOLLOWERS')).toBeTruthy();
        expect(followUser).toHaveBeenCalledExactlyOnceWith('ana');

        await request.resolve();
    });

    it('should_add_one_to_the_count_when_followed', async () => {
        renderCard({ followersCount: 41 });

        await user.click(followButton());

        expect(screen.getByText('42 FOLLOWERS')).toBeTruthy();
    });

    it('should_tell_the_layout_only_after_the_server_has_answered', async () => {
        const request = holdRequest(followUser);
        const { onFollowChanged } = renderCard();

        await user.click(followButton());

        expect(onFollowChanged).not.toHaveBeenCalled();

        await request.resolve();

        expect(onFollowChanged).toHaveBeenCalledTimes(1);
    });
});

describe('unfollowing', () => {
    it('should_arm_the_button_on_the_first_tap_and_send_nothing', async () => {
        renderCard({ followedByMe: true });

        await user.click(followButton());

        expect(visibleLabel()).toBe('UNFOLLOW?');
        expect(followButton().getAttribute('data-phase')).toBe('armed');
        expect(unfollowUser).not.toHaveBeenCalled();
    });

    it('should_keep_the_count_and_stay_pressed_while_armed', async () => {
        renderCard({ followedByMe: true });

        await user.click(followButton());

        expect(screen.getByText('1.2K FOLLOWERS')).toBeTruthy();
        expect(followButton().getAttribute('aria-pressed')).toBe('true');
    });

    it('should_announce_politely_that_a_second_press_unfollows', async () => {
        renderCard({ followedByMe: true });

        await user.click(followButton());

        expect(screen.getByText('Press again to unfollow ana')).toBeTruthy();
        expect(screen.getByText('Press again to unfollow ana').getAttribute('aria-live')).toBe('polite');
    });

    it('should_unfollow_and_take_one_off_the_count_on_the_second_tap', async () => {
        renderCard({
            followedByMe: true,
            followersCount: 42,
        });

        await user.click(followButton());
        await user.click(followButton());

        expect(visibleLabel()).toBe('FOLLOW');
        expect(followButton().getAttribute('aria-pressed')).toBe('false');
        expect(screen.getByText('41 FOLLOWERS')).toBeTruthy();
        expect(unfollowUser).toHaveBeenCalledExactlyOnceWith('ana');
    });

    it('should_tell_the_layout_when_the_unfollow_went_through', async () => {
        const { onFollowChanged } = renderCard({ followedByMe: true });

        await user.click(followButton());
        await user.click(followButton());

        expect(onFollowChanged).toHaveBeenCalledTimes(1);
    });

    it('should_disarm_after_three_seconds', async () => {
        renderCard({ followedByMe: true });
        await user.click(followButton());

        await advance(2999);
        expect(visibleLabel()).toBe('UNFOLLOW?');

        await advance(1);
        expect(visibleLabel()).toBe('FOLLOWING');
        expect(unfollowUser).not.toHaveBeenCalled();
    });

    it('should_disarm_when_the_button_loses_focus', async () => {
        renderCard({ followedByMe: true });
        await user.click(followButton());

        await user.tab();

        expect(visibleLabel()).toBe('FOLLOWING');
        expect(unfollowUser).not.toHaveBeenCalled();
    });

    it('should_disarm_on_escape', async () => {
        renderCard({ followedByMe: true });
        await user.click(followButton());

        await user.keyboard('{Escape}');

        expect(visibleLabel()).toBe('FOLLOWING');
        expect(unfollowUser).not.toHaveBeenCalled();
    });

    it('should_arm_again_on_the_next_tap_after_it_disarmed', async () => {
        renderCard({ followedByMe: true });
        await user.click(followButton());
        await advance(3000);

        await user.click(followButton());

        expect(visibleLabel()).toBe('UNFOLLOW?');
        expect(unfollowUser).not.toHaveBeenCalled();
    });

    it('should_not_unfollow_when_the_arming_timer_of_an_earlier_tap_runs_out_after_the_second_tap', async () => {
        renderCard({ followedByMe: true });
        await user.click(followButton());
        await advance(2000);
        await user.click(followButton());

        await advance(5000);

        expect(visibleLabel()).toBe('FOLLOW');
    });
});

describe('a follow that fails', () => {
    beforeEach(() => {
        vi.mocked(followUser).mockRejectedValue(new Error('The request failed.'));
    });

    it('should_put_the_state_and_the_count_back_and_show_try_again', async () => {
        renderCard();

        await user.click(followButton());
        await advance(0);

        expect(visibleLabel()).toBe('TRY AGAIN');
        expect(followButton().getAttribute('data-phase')).toBe('failed');
        expect(followButton().getAttribute('aria-pressed')).toBe('false');
        expect(screen.getByText('1.2K FOLLOWERS')).toBeTruthy();
    });

    it('should_announce_politely_that_it_could_not_follow', async () => {
        renderCard();

        await user.click(followButton());
        await advance(0);

        expect(screen.getByText('Couldn\'t follow ana').getAttribute('aria-live')).toBe('polite');
    });

    it('should_not_tell_the_layout', async () => {
        const { onFollowChanged } = renderCard();

        await user.click(followButton());
        await advance(0);

        expect(onFollowChanged).not.toHaveBeenCalled();
    });

    it('should_show_the_real_state_again_after_three_seconds', async () => {
        renderCard();
        await user.click(followButton());
        await advance(0);

        await advance(FAILURE_RESET_MS - 1);
        expect(visibleLabel()).toBe('TRY AGAIN');

        await advance(1);
        expect(visibleLabel()).toBe('FOLLOW');
        expect(followButton().getAttribute('data-phase')).toBe('idle');
    });

    it('should_repeat_the_follow_when_try_again_is_tapped', async () => {
        renderCard();
        await user.click(followButton());
        await advance(0);
        vi.mocked(followUser).mockResolvedValue(undefined);

        await user.click(followButton());
        await advance(0);

        expect(followUser).toHaveBeenCalledTimes(2);
        expect(visibleLabel()).toBe('FOLLOWING');
        expect(screen.getByText('1.2K FOLLOWERS')).toBeTruthy();
    });

    it('should_stay_on_the_real_state_after_a_retry_succeeds_and_the_old_timer_would_have_fired', async () => {
        const { onFollowChanged } = renderCard();
        await user.click(followButton());
        await advance(0);
        vi.mocked(followUser).mockResolvedValue(undefined);
        await user.click(followButton());
        await advance(0);

        await advance(FAILURE_RESET_MS);

        expect(visibleLabel()).toBe('FOLLOWING');
        expect(onFollowChanged).toHaveBeenCalledTimes(1);
    });
});

describe('an unfollow that fails', () => {
    beforeEach(() => {
        vi.mocked(unfollowUser).mockRejectedValue(new Error('The request failed.'));
    });

    it('should_put_the_followed_state_and_the_count_back_and_show_try_again', async () => {
        renderCard({ followedByMe: true });

        await user.click(followButton());
        await advance(0);
        await user.click(followButton());
        await advance(0);

        expect(visibleLabel()).toBe('TRY AGAIN');
        expect(followButton().getAttribute('aria-pressed')).toBe('true');
        expect(screen.getByText('1.2K FOLLOWERS')).toBeTruthy();
    });

    it('should_announce_politely_that_it_could_not_unfollow', async () => {
        renderCard({ followedByMe: true });

        await user.click(followButton());
        await advance(0);
        await user.click(followButton());
        await advance(0);

        expect(screen.getByText('Couldn\'t unfollow ana')).toBeTruthy();
    });

    it('should_repeat_the_unfollow_at_once_without_arming_again_when_try_again_is_tapped', async () => {
        renderCard({ followedByMe: true });
        await user.click(followButton());
        await advance(0);
        await user.click(followButton());
        await advance(0);

        await user.click(followButton());
        await advance(0);

        expect(unfollowUser).toHaveBeenCalledTimes(2);
    });

    it('should_show_following_again_after_three_seconds', async () => {
        renderCard({ followedByMe: true });
        await user.click(followButton());
        await advance(0);
        await user.click(followButton());
        await advance(0);

        await advance(FAILURE_RESET_MS);

        expect(visibleLabel()).toBe('FOLLOWING');
    });
});

describe('a card that goes away', () => {
    it('should_leave_no_timer_behind_when_it_unmounts_while_armed', async () => {
        const { unmount } = render(
            <ul>
                <PersonCard person={{ ...PERSON, followedByMe: true }} onFollowChanged={vi.fn()} />
            </ul>,
        );
        await user.click(followButton());

        unmount();

        expect(vi.getTimerCount()).toBe(0);
    });
});
