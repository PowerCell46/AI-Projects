import { expect } from '@playwright/test';
import { APP_PATHS, follow, openAs, test, TEST_TIMEOUT_MS } from './fixtures';
import { waitForCall } from './page-helpers';

const NO_CONTENT_STATUS = 204;

test.describe.configure({ timeout: TEST_TIMEOUT_MS });

test(
    'should_show_1_following_on_the_own_profile_and_1_follower_on_the_followed_profile_after_a_follow',
    async ({ page, createAccount }) => {
        const ana = await createAccount();
        const bob = await createAccount();
        await follow(ana, bob);

        await openAs(page, ana, APP_PATHS.user(ana.user.username));

        await expect(page.getByLabel('Profile statistics')).toContainText('1 FOLLOWING');
        await expect(page.getByLabel('Profile statistics')).toContainText('0 FOLLOWERS');

        await page.goto(APP_PATHS.user(bob.user.username));

        await expect(page.getByLabel('Profile statistics')).toContainText('1 FOLLOWER');
        await expect(page.getByLabel('Profile statistics')).toContainText('0 FOLLOWING');
    },
);

test(
    'should_offer_no_follow_button_when_the_user_opens_their_own_profile',
    async ({ page, createAccount }) => {
        const ana = await createAccount();

        await openAs(page, ana, APP_PATHS.user(ana.user.username));

        await expect(page.getByRole('button', { name: 'EDIT', exact: true })).toBeVisible();
        await expect(page.getByRole('button', { name: `Follow ${ana.user.username}` })).toHaveCount(0);
    },
);

test(
    'should_drop_the_follower_count_to_0_and_keep_it_after_a_reload_when_the_follow_is_undone_on_the_profile',
    async ({ page, createAccount }) => {
        const ana = await createAccount();
        const bob = await createAccount();
        await follow(ana, bob);
        await openAs(page, ana, APP_PATHS.user(bob.user.username));
        const followButton = page.getByRole('button', { name: `Follow ${bob.user.username}` });
        await expect(followButton).toHaveText('FOLLOWING');
        await expect(page.getByLabel('Profile statistics')).toContainText('1 FOLLOWER');

        await followButton.click();

        await expect(followButton).toHaveText('UNFOLLOW?');

        const unfollowRequest = waitForCall(page, 'DELETE', `/users/${bob.user.username}/follow`);

        await followButton.click();

        await expect(followButton).toHaveText('FOLLOW');
        await expect(page.getByLabel('Profile statistics')).toContainText('0 FOLLOWERS');
        expect((await unfollowRequest).status()).toBe(NO_CONTENT_STATUS);

        await page.reload();

        await expect(followButton).toHaveText('FOLLOW');
        await expect(page.getByLabel('Profile statistics')).toContainText('0 FOLLOWERS');
    },
);
