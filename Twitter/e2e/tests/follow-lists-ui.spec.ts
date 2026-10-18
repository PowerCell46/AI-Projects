import { expect, type Locator, type Page } from '@playwright/test';
import { APP_PATHS, follow, openAs, test, TEST_TIMEOUT_MS, type Account } from './fixtures';
import { waitForCall } from './page-helpers';

const NO_CONTENT_STATUS = 204;

test.describe.configure({ timeout: TEST_TIMEOUT_MS });

function dialog(page: Page): Locator {
    return page.getByRole('dialog');
}

function stat(page: Page, name: RegExp): Locator {
    return page
        .getByLabel('Profile statistics')
        .getByRole('button', { name });
}

function cardOf(page: Page, account: Account): Locator {
    return dialog(page)
        .getByRole('listitem')
        .filter({ has: page.getByText(account.user.username, { exact: true }) });
}

function followButtonOf(page: Page, account: Account): Locator {
    return dialog(page).getByRole('button', { name: `Follow ${account.user.username}` });
}

async function openList(page: Page, kind: 'FOLLOWERS' | 'FOLLOWING', statName: RegExp) {
    await stat(page, statName).click();

    await expect(dialog(page)).toBeVisible();
    await expect(dialog(page).getByRole('heading', { name: kind })).toBeVisible();
}

test(
    'should_list_the_followers_of_another_profile_with_a_follow_button_on_every_card_but_the_readers_own',
    async ({ page, createAccount }) => {
        const ana = await createAccount();
        const bob = await createAccount();
        const cat = await createAccount();
        await follow(ana, bob);
        await follow(cat, bob);
        await openAs(page, ana, APP_PATHS.user(bob.user.username));

        await openList(page, 'FOLLOWERS', /2 FOLLOWERS$/);

        await expect(cardOf(page, cat)).toBeVisible();
        await expect(cardOf(page, ana)).toBeVisible();
        await expect(followButtonOf(page, cat)).toHaveText('FOLLOW');
        await expect(followButtonOf(page, ana)).toHaveCount(0);
    },
);

test(
    'should_list_the_people_another_profile_follows_with_their_follow_state_for_the_reader',
    async ({ page, createAccount }) => {
        const ana = await createAccount();
        const bob = await createAccount();
        const cat = await createAccount();
        const dee = await createAccount();
        await follow(bob, cat);
        await follow(bob, dee);
        await follow(ana, dee);
        await openAs(page, ana, APP_PATHS.user(bob.user.username));

        await openList(page, 'FOLLOWING', /2 FOLLOWING$/);

        await expect(followButtonOf(page, cat)).toHaveText('FOLLOW');
        await expect(followButtonOf(page, dee)).toHaveText('FOLLOWING');
    },
);

test(
    'should_follow_from_the_list_and_keep_it_after_a_reload',
    async ({ page, createAccount }) => {
        const ana = await createAccount();
        const bob = await createAccount();
        const cat = await createAccount();
        await follow(cat, bob);
        await openAs(page, ana, APP_PATHS.user(bob.user.username));
        await openList(page, 'FOLLOWERS', /1 FOLLOWER$/);
        const followRequest = waitForCall(page, 'PUT', `/users/${cat.user.username}/follow`);

        await followButtonOf(page, cat).click();

        await expect(followButtonOf(page, cat)).toHaveText('FOLLOWING');
        expect((await followRequest).status()).toBe(NO_CONTENT_STATUS);

        // The open list is part of the history entry, so it is still open after a reload.
        await page.reload();

        await expect(dialog(page).getByRole('heading', { name: 'FOLLOWERS' })).toBeVisible();
        await expect(followButtonOf(page, cat)).toHaveText('FOLLOWING');
    },
);

test(
    'should_drop_the_following_count_of_the_own_profile_when_someone_is_unfollowed_from_the_list',
    async ({ page, createAccount }) => {
        const ana = await createAccount();
        const bob = await createAccount();
        await follow(ana, bob);
        await openAs(page, ana, APP_PATHS.user(ana.user.username));
        await openList(page, 'FOLLOWING', /1 FOLLOWING$/);
        const unfollowRequest = waitForCall(page, 'DELETE', `/users/${bob.user.username}/follow`);

        await followButtonOf(page, bob).click();

        await expect(followButtonOf(page, bob)).toHaveText('UNFOLLOW?');

        await followButtonOf(page, bob).click();

        await expect(followButtonOf(page, bob)).toHaveText('FOLLOW');
        expect((await unfollowRequest).status()).toBe(NO_CONTENT_STATUS);
        await expect(stat(page, /FOLLOWING$/)).toHaveText('0 FOLLOWING');
    },
);

test(
    'should_say_there_is_nobody_when_the_profile_has_no_followers',
    async ({ page, createAccount }) => {
        const ana = await createAccount();
        await openAs(page, ana, APP_PATHS.user(ana.user.username));

        await openList(page, 'FOLLOWERS', /0 FOLLOWERS$/);

        await expect(dialog(page).getByText('NO FOLLOWERS YET')).toBeVisible();
    },
);

test(
    'should_close_with_the_close_button_and_give_focus_back_to_the_stat',
    async ({ page, createAccount }) => {
        const ana = await createAccount();
        await openAs(page, ana, APP_PATHS.user(ana.user.username));
        await openList(page, 'FOLLOWERS', /0 FOLLOWERS$/);

        await dialog(page)
            .getByRole('button', { name: 'CLOSE' })
            .click();

        await expect(dialog(page)).toHaveCount(0);
        await expect(stat(page, /0 FOLLOWERS$/)).toBeFocused();
    },
);

test(
    'should_close_with_escape',
    async ({ page, createAccount }) => {
        const ana = await createAccount();
        await openAs(page, ana, APP_PATHS.user(ana.user.username));
        await openList(page, 'FOLLOWERS', /0 FOLLOWERS$/);
        await expect(dialog(page).getByRole('button', { name: 'CLOSE' })).toBeFocused();

        await page.keyboard.press('Escape');

        await expect(dialog(page)).toHaveCount(0);
    },
);

test(
    'should_close_and_stay_on_the_profile_when_the_browser_goes_back',
    async ({ page, createAccount }) => {
        const ana = await createAccount();
        const bob = await createAccount();
        await openAs(page, ana, APP_PATHS.user(bob.user.username));
        await openList(page, 'FOLLOWERS', /0 FOLLOWERS$/);

        await page.goBack();

        await expect(dialog(page)).toHaveCount(0);
        await expect(page).toHaveURL(new RegExp(`${APP_PATHS.user(bob.user.username)}$`));
        await expect(page.getByRole('heading', { level: 1, name: bob.user.username })).toBeVisible();
    },
);

test(
    'should_open_the_profile_of_a_card_and_reopen_the_list_when_the_browser_goes_back',
    async ({ page, createAccount }) => {
        const ana = await createAccount();
        const bob = await createAccount();
        const cat = await createAccount();
        await follow(cat, bob);
        await openAs(page, ana, APP_PATHS.user(bob.user.username));
        await openList(page, 'FOLLOWERS', /1 FOLLOWER$/);

        await dialog(page)
            .getByRole('link', { name: cat.user.username })
            .click();

        await expect(page).toHaveURL(new RegExp(`${APP_PATHS.user(cat.user.username)}$`));
        await expect(dialog(page)).toHaveCount(0);
        await expect(page.getByRole('heading', { level: 1, name: cat.user.username })).toBeVisible();

        await page.goBack();

        await expect(dialog(page).getByRole('heading', { name: 'FOLLOWERS' })).toBeVisible();
        await expect(cardOf(page, cat)).toBeVisible();
    },
);
