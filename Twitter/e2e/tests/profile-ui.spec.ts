import { expect, type Locator, type Page, type Request } from '@playwright/test';
import {
    expectFeedToContain,
    FEED_POLL_TIMEOUT_MS,
    follow,
    MAX_PAGE_SIZE,
    openAs,
    postTweet,
    test,
    TINY_PNG,
    type Account,
} from './fixtures';


// Registrations each wait for a confirmation email, and a follow crosses the outbox and Kafka before the feed fills.
const TEST_TIMEOUT_MS = 120_000;

// How long one check that a card is on the page may take before the list is scrolled further.
const PERSON_CHECK_MS = 1_000;

// How long one check that the end of the list is visible may take before the scroll is retried.
const END_OF_LIST_CHECK_MS = 1_000;

const BULK_POST_COUNT = 25;

const BIO = 'Sounding the deep.';

const LOCATION = 'Mariana Trench';

test.describe.configure({ timeout: TEST_TIMEOUT_MS });

function profileHeading(page: Page): Locator {
    return page.getByRole('heading', {
        level: 1,
        name: /\S/,
    });
}

function tweetsHeading(page: Page): Locator {
    return page.getByRole('heading', { level: 2 });
}

function postWithText(page: Page, text: string): Locator {
    return page
        .getByRole('article')
        .filter({ hasText: text });
}

function mastheadPicture(page: Page): Locator {
    return page.locator('.profile-masthead .avatar');
}

function headerPicture(page: Page): Locator {
    return page.locator('.user-menu-picture');
}

function isProfileUpdate(request: Request): boolean {
    const url = request.url();

    return request.method() === 'PATCH' && url.endsWith('/users/me');
}

function patchRequest(page: Page): Promise<Request> {
    return page.waitForRequest(isProfileUpdate);
}

function pictureWidth(picture: Locator): Promise<number> {
    return picture.evaluate((element: HTMLImageElement) => element.naturalWidth);
}

// The database is shared with the specs running in parallel, so what a test looks for can sit below the first page.
async function scrollUntilVisible(page: Page, target: Locator, checkMs: number) {
    await expect(async () => {
        await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
        await expect(target).toBeVisible({ timeout: checkMs });
    }).toPass({ timeout: FEED_POLL_TIMEOUT_MS });
}

async function openOwnProfileFromMenu(page: Page) {
    await page
        .getByRole('button', { name: 'Account menu' })
        .click();
    await page
        .getByRole('menuitem', { name: 'PROFILE' })
        .click();
}

async function openEditSheet(page: Page): Promise<Locator> {
    await page
        .getByRole('button', { name: 'EDIT' })
        .click();

    const sheet = page.getByRole('dialog', { name: 'Say who you are.' });

    await expect(sheet).toBeVisible();

    return sheet;
}

async function saveEditSheet(sheet: Locator) {
    await sheet
        .getByRole('button', { name: 'SAVE CHANGES' })
        .click();
}

async function findPersonCard(page: Page, username: string): Promise<Locator> {
    const card = page
        .getByRole('listitem')
        .filter({ has: page.getByText(username, { exact: true }) });

    await scrollUntilVisible(page, card, PERSON_CHECK_MS);

    return card;
}

async function postBulk(author: Account, count: number) {
    for (let index = 1; index <= count; index++) {
        await postTweet(author, `profile bulk post ${index}`);
    }
}

test(
    'should_open_the_own_profile_from_the_menu_with_an_empty_bio_and_location_left_out',
    async ({ page, createAccount }) => {
        const ana = await createAccount();
        await openAs(page, ana, '/feed');

        await openOwnProfileFromMenu(page);

        await expect(page).toHaveURL(new RegExp(`/users/${ana.user.username}$`));
        await expect(profileHeading(page)).toHaveText(ana.user.username);
        await expect(page.getByRole('button', { name: 'EDIT' })).toBeVisible();
        await expect(page.locator('.profile-masthead-bio')).toHaveCount(0);
        await expect(page.locator('.profile-masthead-meta')).not.toContainText(LOCATION);
        await expect(page.getByText(/^JOINED /)).toBeVisible();
    },
);

test(
    'should_show_the_new_bio_and_location_and_keep_them_after_a_reload_when_the_profile_is_edited',
    async ({ page, createAccount }) => {
        const ana = await createAccount();
        await openAs(page, ana, `/users/${ana.user.username}`);
        const sheet = await openEditSheet(page);

        await sheet
            .getByLabel('BIO')
            .fill(BIO);
        await sheet
            .getByLabel('LOCATION')
            .fill(LOCATION);
        await saveEditSheet(sheet);

        await expect(sheet).toBeHidden();
        await expect(page.locator('.profile-masthead-bio')).toHaveText(BIO);
        await expect(page.locator('.profile-masthead-meta')).toContainText(LOCATION.toUpperCase());
        await expect(page.getByRole('button', { name: 'EDIT' })).toBeFocused();

        await page.reload();

        await expect(page.locator('.profile-masthead-bio')).toHaveText(BIO);
        await expect(page.locator('.profile-masthead-meta')).toContainText(LOCATION.toUpperCase());
    },
);

test(
    'should_send_only_the_location_and_keep_the_bio_when_only_the_location_is_changed',
    async ({ page, createAccount }) => {
        const ana = await createAccount();
        const saved = await ana.api.patch('/api/v1/users/me', { data: { bio: BIO } });
        expect(saved.ok()).toBe(true);
        await openAs(page, ana, `/users/${ana.user.username}`);
        const sheet = await openEditSheet(page);
        const request = patchRequest(page);

        await sheet
            .getByLabel('LOCATION')
            .fill(LOCATION);
        await saveEditSheet(sheet);

        expect((await request).postDataJSON()).toEqual({ location: LOCATION });
        await expect(sheet).toBeHidden();
        await expect(page.locator('.profile-masthead-bio')).toHaveText(BIO);
        await expect(page.locator('.profile-masthead-meta')).toContainText(LOCATION.toUpperCase());
    },
);

test(
    'should_ask_to_discard_changes_on_escape_and_close_on_the_second_escape_when_the_sheet_is_dirty',
    async ({ page, createAccount }) => {
        const ana = await createAccount();
        await openAs(page, ana, `/users/${ana.user.username}`);
        const sheet = await openEditSheet(page);

        await sheet
            .getByLabel('BIO')
            .fill(BIO);
        await page.keyboard.press('Escape');

        await expect(sheet.getByRole('button', { name: 'DISCARD CHANGES?' })).toBeVisible();
        await expect(sheet).toBeVisible();

        await page.keyboard.press('Escape');

        await expect(sheet).toBeHidden();
        await expect(page.locator('.profile-masthead-bio')).toHaveCount(0);
    },
);

test(
    'should_change_the_masthead_and_header_pictures_when_a_photo_is_uploaded',
    async ({ page, createAccount }) => {
        const ana = await createAccount();
        await openAs(page, ana, `/users/${ana.user.username}`);
        const mastheadBefore = await mastheadPicture(page)
            .getAttribute('src');
        const headerBefore = await headerPicture(page)
            .getAttribute('src');
        const sheet = await openEditSheet(page);

        await sheet
            .getByLabel('CHANGE PHOTO')
            .setInputFiles(TINY_PNG);
        await saveEditSheet(sheet);

        await expect(sheet).toBeHidden();
        await expect(mastheadPicture(page)).not.toHaveAttribute('src', mastheadBefore ?? '');
        await expect(headerPicture(page)).not.toHaveAttribute('src', headerBefore ?? '');
        await expect
            .poll(() => pictureWidth(mastheadPicture(page)))
            .toBeGreaterThan(0);
    },
);

test(
    'should_open_the_author_profile_from_the_feed_follow_there_and_return_to_the_same_post_with_back',
    async ({ page, createAccount }) => {
        const ana = await createAccount();
        const bob = await createAccount();
        const content = `a post by ${bob.user.username}`;
        await follow(ana, bob);
        const tweetId = await postTweet(bob, content);
        await expectFeedToContain(ana, tweetId);
        await openAs(page, ana, '/feed');
        const post = postWithText(page, content);
        await expect(post).toBeVisible();

        await post
            .getByRole('link', { name: bob.user.username })
            .click();

        await expect(page).toHaveURL(new RegExp(`/users/${bob.user.username}$`));
        await expect(profileHeading(page)).toHaveText(bob.user.username);
        await expect(page.getByRole('button', { name: 'EDIT' })).toBeHidden();
        await expect(postWithText(page, content)).toBeVisible();

        await page.goBack();

        await expect(page).toHaveURL(/\/feed$/);
        await expect(postWithText(page, content)).toBeInViewport();
    },
);

test(
    'should_follow_from_the_profile_and_count_one_more_follower_when_follow_is_pressed',
    async ({ page, createAccount }) => {
        const ana = await createAccount();
        const bob = await createAccount();
        await openAs(page, ana, `/users/${bob.user.username}`);
        const button = page.getByRole('button', { name: `Follow ${bob.user.username}` });
        await expect(button).toHaveText('FOLLOW');
        await expect(page.getByText('0 FOLLOWERS')).toBeVisible();

        await button.click();

        await expect(button).toHaveText('FOLLOWING');
        await expect(page.getByText('1 FOLLOWER', { exact: true })).toBeVisible();
    },
);

test(
    'should_open_the_profile_from_a_people_card_but_not_when_follow_is_pressed',
    async ({ page, createAccount }) => {
        const ana = await createAccount();
        const bob = await createAccount();
        await openAs(page, ana, '/users');
        const card = await findPersonCard(page, bob.user.username);

        await card
            .getByRole('button', { name: `Follow ${bob.user.username}` })
            .click();

        await expect(card.getByRole('button', { name: `Follow ${bob.user.username}` })).toHaveText('FOLLOWING');
        await expect(page).toHaveURL(/\/users$/);

        await card
            .getByText(/FOLLOWERS?$/)
            .click();

        await expect(page).toHaveURL(new RegExp(`/users/${bob.user.username}$`));
        await expect(profileHeading(page)).toHaveText(bob.user.username);
    },
);

test(
    'should_say_user_not_found_when_the_profile_does_not_exist',
    async ({ page, createAccount }) => {
        const ana = await createAccount();

        await openAs(page, ana, '/users/nobody_here');

        await expect(page.getByText('USER NOT FOUND')).toBeVisible();
    },
);

test(
    'should_load_a_second_page_and_end_the_list_when_the_author_has_25_tweets',
    async ({ page, createAccount }) => {
        const ana = await createAccount();
        const bob = await createAccount();
        await postBulk(bob, BULK_POST_COUNT);
        await openAs(page, ana, `/users/${bob.user.username}`);
        await expect(tweetsHeading(page)).toHaveText(`TWEETS · ${BULK_POST_COUNT}`);

        await scrollUntilVisible(page, page.getByText('END OF TWEETS'), END_OF_LIST_CHECK_MS);

        await expect(page.getByRole('article')).toHaveCount(BULK_POST_COUNT);
        expect(BULK_POST_COUNT).toBeLessThanOrEqual(MAX_PAGE_SIZE);
    },
);

test(
    'should_show_a_new_post_at_the_top_and_raise_the_count_when_the_reader_posts_from_their_own_profile',
    async ({ page, createAccount }) => {
        const ana = await createAccount();
        await postTweet(ana, 'an older post');
        const content = `fresh from the profile of ${ana.user.username}`;
        await openAs(page, ana, `/users/${ana.user.username}`);
        await expect(tweetsHeading(page)).toHaveText('TWEETS · 1');

        await page
            .getByRole('button', { name: 'Post' })
            .click();
        await page
            .getByRole('textbox', { name: "What's worth sending up?" })
            .fill(content);
        await page
            .getByRole('button', { name: /^PUBLISH/ })
            .click();

        await expect(page.getByRole('dialog')).toBeHidden();
        const firstPost = page
            .getByRole('article')
            .first();

        await expect(firstPost).toContainText(content);
        await expect(tweetsHeading(page)).toHaveText('TWEETS · 2');
    },
);
