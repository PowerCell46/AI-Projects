import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { me } from '../../api/auth';
import { fetchAuthorTweets } from '../../api/authorTweets';
import { fetchFeed } from '../../api/feed';
import { ApiError } from '../../api/http';
import { fetchTweetCount } from '../../api/tweets';
import { fetchUserProfile, updateProfile, uploadProfilePicture } from '../../api/users';
import { reportViews } from '../../api/views';
import { profilePath } from '../../routes';
import { renderApp } from '../../test/renderApp';
import { advance } from '../../test/stepFlowHelpers';
import { tweetItem } from '../../test/tweetItem';
import { userProfile } from '../../test/userProfile';


vi.mock('../../api/auth', async (importOriginal) => ({
    ...await importOriginal<typeof import('../../api/auth')>(),
    me: vi.fn(),
}));

vi.mock('../../api/feed', () => ({
    fetchFeed: vi.fn(),
}));

vi.mock('../../api/authorTweets', () => ({
    fetchAuthorTweets: vi.fn(),
}));

vi.mock('../../api/tweets', () => ({
    fetchTweetCount: vi.fn(),
    publishTweet: vi.fn(),
}));

vi.mock('../../api/users', () => ({
    fetchUserProfile: vi.fn(),
    updateProfile: vi.fn(),
    uploadProfilePicture: vi.fn(),
    followUser: vi.fn(),
    unfollowUser: vi.fn(),
}));

vi.mock('../../api/views', () => ({
    reportViews: vi.fn(),
}));

vi.mock('../../api/likes', () => ({
    likeTweet: vi.fn(),
    unlikeTweet: vi.fn(),
}));

vi.mock('../../api/savedTweets', () => ({
    fetchSavedTweets: vi.fn(),
    saveTweet: vi.fn(),
    unsaveTweet: vi.fn(),
}));

const SIGNED_IN_USER = {
    id: 'user-1',
    username: 'peter_g',
    email: 'peter@example.com',
};

const ANA = userProfile({
    id: 'author-1',
    username: 'ana_b',
    bio: 'Builds boats.',
    location: 'Sofia, Bulgaria',
    createdAt: '2026-10-04T10:00:00Z',
    followersCount: 1204,
    followingCount: 31,
});

const ANAS_POST = tweetItem({
    id: 'tweet-1',
    content: 'a post by ana',
});

const OWN_PROFILE = userProfile({
    id: SIGNED_IN_USER.id,
    username: SIGNED_IN_USER.username,
    bio: 'My old bio',
    location: 'Sofia',
});

let user: ReturnType<typeof userEvent.setup>;

function openMain() {
    return within(screen.getByRole('main'));
}

function headingNamed(level: number, name: string): HTMLElement {
    return openMain().getByRole(
        'heading',
        {
            level,
            name,
        },
    );
}

function statTexts(): string[] {
    return within(openMain().getByRole('list', { name: 'Profile statistics' }))
        .getAllByRole('listitem')
        .map((item) => item.textContent ?? '');
}

function showProfileOf(profile: ReturnType<typeof userProfile>) {
    vi.mocked(fetchUserProfile)
        .mockImplementation(async (username) => (
            username.toLowerCase() === SIGNED_IN_USER.username ? OWN_PROFILE : profile
        ));
}

beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'requestAnimationFrame', 'cancelAnimationFrame'] });
    user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    vi.mocked(me)
        .mockResolvedValue(SIGNED_IN_USER);
    showProfileOf(ANA);
    vi.mocked(fetchFeed)
        .mockReset()
        .mockResolvedValue({
            items: [],
            nextCursor: null,
        });
    vi.mocked(fetchTweetCount)
        .mockReset()
        .mockResolvedValue(25);
    vi.mocked(fetchAuthorTweets)
        .mockReset()
        .mockResolvedValue({
            items: [ANAS_POST],
            nextCursor: null,
        });
    vi.mocked(reportViews)
        .mockReset()
        .mockResolvedValue(undefined);
});

afterEach(() => {
    vi.useRealTimers();
});

describe('the masthead', () => {
    it('should_show_the_username_once_as_the_heading_and_no_handle', async () => {
        await renderApp(profilePath('ana_b'));

        expect(headingNamed(1, 'ana_b')).toBeTruthy();
        expect(openMain().queryByText('@ana_b')).toBeNull();
    });

    it('should_show_the_bio_the_location_in_capitals_and_the_join_month', async () => {
        await renderApp(profilePath('ana_b'));

        expect(openMain().getByText('Builds boats.')).toBeTruthy();
        expect(openMain().getByText('SOFIA, BULGARIA')).toBeTruthy();
        expect(openMain().getByText('JOINED OCT 2026')).toBeTruthy();
    });

    it('should_leave_out_the_bio_and_the_location_when_they_are_empty', async () => {
        showProfileOf(userProfile({
            ...ANA,
            bio: null,
            location: null,
        }));

        await renderApp(profilePath('ana_b'));

        expect(openMain().getByText('JOINED OCT 2026')).toBeTruthy();
        expect(document.querySelector('.profile-masthead-bio')).toBeNull();
        expect(document.querySelector('.profile-masthead-meta')?.children).toHaveLength(1);
    });

    it('should_leave_out_a_bio_and_a_location_that_are_only_blanks_and_direction_controls', async () => {
        showProfileOf(userProfile({
            ...ANA,
            bio: '  ‮  ',
            location: '⁦ ',
        }));

        await renderApp(profilePath('ana_b'));

        expect(document.querySelector('.profile-masthead-bio')).toBeNull();
        expect(document.querySelector('.profile-masthead-meta')?.children).toHaveLength(1);
    });

    it('should_strip_direction_controls_from_the_bio', async () => {
        showProfileOf(userProfile({
            ...ANA,
            bio: 'abc‮def',
        }));

        await renderApp(profilePath('ana_b'));

        expect(document.querySelector('.profile-masthead-bio')?.textContent).toBe('abcdef');
    });

    it('should_show_the_default_picture_when_the_user_has_none_and_the_picture_when_there_is_one', async () => {
        showProfileOf(userProfile({
            ...ANA,
            profilePictureUrl: 'http://localhost/api/v1/files/pic-1',
        }));

        await renderApp(profilePath('ana_b'));

        const avatar = document.querySelector('.profile-masthead .avatar');

        expect(avatar?.getAttribute('src')).toBe('http://localhost/api/v1/files/pic-1');
        expect(avatar?.getAttribute('data-size')).toBe('huge');
    });

    it('should_not_show_the_email_or_the_birthdate', async () => {
        await renderApp(profilePath('ana_b'));

        expect(openMain().queryByText(/@example\.com/)).toBeNull();
        expect(openMain().queryByText(/BORN|BIRTH/i)).toBeNull();
    });
});

describe('the stats', () => {
    it('should_read_each_number_with_its_label_in_full', async () => {
        await renderApp(profilePath('ana_b'));

        expect(statTexts()).toEqual(['1,204 FOLLOWERS', '31 FOLLOWING', '25 TWEETS']);
    });

    it('should_use_the_singular_when_a_count_is_one', async () => {
        showProfileOf(userProfile({
            ...ANA,
            followersCount: 1,
        }));
        vi.mocked(fetchTweetCount)
            .mockResolvedValue(1);

        await renderApp(profilePath('ana_b'));

        expect(statTexts()).toEqual(['1 FOLLOWER', '31 FOLLOWING', '1 TWEET']);
    });

    it('should_not_make_the_stats_clickable', async () => {
        await renderApp(profilePath('ana_b'));

        const stats = openMain().getByRole('list', { name: 'Profile statistics' });

        expect(stats.querySelector('a, button')).toBeNull();
    });
});

describe('the tweets section', () => {
    it('should_head_the_list_with_the_count_and_show_the_tweets_of_the_author', async () => {
        await renderApp(profilePath('ana_b'));

        expect(headingNamed(2, 'TWEETS · 25')).toBeTruthy();
        expect(openMain().getByText('a post by ana')).toBeTruthy();
    });

    it('should_read_the_count_and_the_first_page_with_the_id_of_the_profile', async () => {
        await renderApp(profilePath('ana_b'));

        expect(fetchTweetCount).toHaveBeenCalledExactlyOnceWith('author-1');
        expect(fetchAuthorTweets).toHaveBeenCalledExactlyOnceWith(
            'author-1',
            {
                cursor: null,
                size: 20,
            },
        );
    });

    it('should_show_end_of_tweets_below_the_last_page', async () => {
        await renderApp(profilePath('ana_b'));

        expect(openMain().getByText('END OF TWEETS')).toBeTruthy();
    });

    it('should_show_no_tweets_yet_when_the_author_has_none', async () => {
        vi.mocked(fetchAuthorTweets)
            .mockResolvedValue({
                items: [],
                nextCursor: null,
            });
        vi.mocked(fetchTweetCount)
            .mockResolvedValue(0);

        await renderApp(profilePath('ana_b'));

        expect(openMain().getByText('NO TWEETS YET')).toBeTruthy();
        expect(headingNamed(2, 'TWEETS · 0')).toBeTruthy();
    });

    it('should_leave_the_number_out_and_still_list_the_tweets_when_the_count_cannot_be_read', async () => {
        vi.mocked(fetchTweetCount)
            .mockRejectedValue(new ApiError(502, []));

        await renderApp(profilePath('ana_b'));

        expect(headingNamed(2, 'TWEETS')).toBeTruthy();
        expect(openMain().getByText('a post by ana')).toBeTruthy();
        expect(statTexts()).toEqual(['1,204 FOLLOWERS', '31 FOLLOWING']);
    });

    it('should_show_signal_lost_and_load_the_page_again_when_try_again_is_pressed', async () => {
        vi.mocked(fetchAuthorTweets)
            .mockRejectedValueOnce(new ApiError(502, []));

        await renderApp(profilePath('ana_b'));

        expect(openMain().getByText('SIGNAL LOST')).toBeTruthy();

        await user.click(openMain().getByRole('button', { name: 'TRY AGAIN' }));
        await advance(1);

        expect(openMain().getByText('a post by ana')).toBeTruthy();
    });
});

describe('a profile that cannot be shown', () => {
    it('should_show_user_not_found_when_the_server_answers_404', async () => {
        vi.mocked(fetchUserProfile)
            .mockImplementation(async (username) => {
                if (username === SIGNED_IN_USER.username) {
                    return userProfile({ ...SIGNED_IN_USER });
                }

                throw new ApiError(404, []);
            });

        await renderApp(profilePath('ghost_1'));

        expect(openMain().getByText('USER NOT FOUND')).toBeTruthy();
        expect(fetchTweetCount).not.toHaveBeenCalled();
        expect(fetchAuthorTweets).not.toHaveBeenCalled();
    });

    it('should_show_signal_lost_and_read_the_profile_again_when_try_again_is_pressed', async () => {
        let isFirstAttempt = true;
        vi.mocked(fetchUserProfile)
            .mockImplementation(async (username) => {
                if (username === SIGNED_IN_USER.username) {
                    return userProfile({ ...SIGNED_IN_USER });
                }

                if (isFirstAttempt) {
                    isFirstAttempt = false;
                    throw new ApiError(502, []);
                }

                return ANA;
            });

        await renderApp(profilePath('ana_b'));

        expect(openMain().getByText('SIGNAL LOST')).toBeTruthy();

        await user.click(openMain().getByRole('button', { name: 'TRY AGAIN' }));
        await advance(1);

        expect(headingNamed(1, 'ana_b')).toBeTruthy();
    });

    it('should_show_user_not_found_without_any_request_when_the_username_cannot_exist', async () => {
        await renderApp(profilePath('no'));

        expect(openMain().getByText('USER NOT FOUND')).toBeTruthy();
        expect(fetchUserProfile).not.toHaveBeenCalledWith('no');
        expect(fetchTweetCount).not.toHaveBeenCalled();
        expect(fetchAuthorTweets).not.toHaveBeenCalled();
    });
});

describe('your own profile', () => {
    function editButton(): HTMLElement {
        return openMain().getByRole('button', { name: 'EDIT' });
    }

    it('should_show_edit_and_no_follow_button_on_your_own_profile', async () => {
        await renderApp(profilePath('peter_g'));

        expect(editButton()).toBeTruthy();
        expect(screen.queryByRole('button', { name: /^Follow / })).toBeNull();
    });

    it('should_show_edit_when_the_address_spells_your_username_in_another_case', async () => {
        await renderApp(profilePath('PETER_G'));

        expect(editButton()).toBeTruthy();
    });

    it('should_show_follow_and_no_edit_on_the_profile_of_someone_else', async () => {
        await renderApp(profilePath('ana_b'));

        expect(openMain().queryByRole('button', { name: 'EDIT' })).toBeNull();
        expect(openMain().getByRole('button', { name: 'Follow ana_b' })).toBeTruthy();
    });

    it('should_open_the_edit_sheet_over_the_page_when_edit_is_pressed', async () => {
        await renderApp(profilePath('peter_g'));

        await user.click(editButton());

        const sheet = screen.getByRole('dialog');

        expect(within(sheet).getByText('peter@example.com')).toBeTruthy();
        expect(within(sheet).getByRole('textbox', { name: 'BIO' })).toHaveProperty('value', 'My old bio');
    });

    it('should_give_the_focus_back_to_edit_when_the_sheet_is_cancelled', async () => {
        await renderApp(profilePath('peter_g'));
        await user.click(editButton());

        await user.click(screen.getByRole('button', { name: 'CANCEL ESC' }));

        expect(screen.queryByRole('dialog')).toBeNull();
        expect(document.activeElement).toBe(editButton());
    });

    it('should_update_the_masthead_in_place_and_close_the_sheet_when_the_changes_are_saved', async () => {
        vi.mocked(updateProfile)
            .mockResolvedValue(userProfile({
                ...OWN_PROFILE,
                bio: 'My new bio',
            }));
        await renderApp(profilePath('peter_g'));
        await user.click(editButton());
        await advance(100);
        await user.clear(screen.getByRole('textbox', { name: 'BIO' }));
        await user.paste('My new bio');

        await user.click(screen.getByRole('button', { name: 'SAVE CHANGES' }));
        await advance(1);

        expect(updateProfile).toHaveBeenCalledExactlyOnceWith({ bio: 'My new bio' });
        expect(screen.queryByRole('dialog')).toBeNull();
        expect(openMain().getByText('My new bio')).toBeTruthy();
        expect(openMain().queryByText('My old bio')).toBeNull();
        expect(fetchUserProfile).toHaveBeenCalledTimes(2);
        expect(document.documentElement.dataset.scrollLocked).toBeUndefined();
    });

    it('should_change_the_masthead_and_the_header_avatar_at_once_when_a_new_photo_is_saved', async () => {
        vi.mocked(uploadProfilePicture)
            .mockResolvedValue(userProfile({
                ...OWN_PROFILE,
                profilePictureUrl: 'http://localhost/api/v1/files/pic-new',
            }));
        await renderApp(profilePath('peter_g'));
        await user.click(editButton());
        await user.upload(
            screen.getByLabelText('CHANGE PHOTO'),
            new File(['abc'], 'me.png', { type: 'image/png' }),
        );

        await user.click(screen.getByRole('button', { name: 'SAVE CHANGES' }));
        await advance(1);

        expect(updateProfile).not.toHaveBeenCalled();
        expect(screen.queryByRole('dialog')).toBeNull();
        expect(document.querySelector('.profile-masthead .avatar')?.getAttribute('src'))
            .toBe('http://localhost/api/v1/files/pic-new');
        expect(document.querySelector('.user-menu-picture')?.getAttribute('src'))
            .toBe('http://localhost/api/v1/files/pic-new');
    });

    it('should_keep_the_masthead_as_it_was_when_the_sheet_is_discarded', async () => {
        await renderApp(profilePath('peter_g'));
        await user.click(editButton());
        await user.clear(screen.getByRole('textbox', { name: 'BIO' }));
        await user.paste('changed but not saved');

        await user.click(screen.getByRole('button', { name: 'CANCEL ESC' }));
        await user.click(screen.getByRole('button', { name: 'DISCARD CHANGES?' }));

        expect(openMain().getByText('My old bio')).toBeTruthy();
        expect(updateProfile).not.toHaveBeenCalled();
    });
});
