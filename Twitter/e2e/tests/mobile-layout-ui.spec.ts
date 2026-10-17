import { expect, type Locator, type Page } from '@playwright/test';
import {
    API_PATHS,
    APP_PATHS,
    expectFeedToContain,
    HTTP_STATUS,
    newUser,
    openAs,
    postTweet,
    test,
    TEST_TIMEOUT_MS,
    typeStep,
    type Account,
} from './fixtures';
import {
    COMPOSER_PLACEHOLDER,
    expectNoHorizontalOverflow,
    expectTouchTargetsAtLeast44px,
    postWithText,
} from './page-helpers';

const SAVED_ROUTE = '/saved';

const LIKED_ROUTE = '/liked';

const SAVED_TWEETS_API_PATH = '/api/v1/saved-tweets';

const LIKES_API_PATH = '/api/v1/likes';

// Everything a logged-in user sees: one post that is also saved, liked and replied to, and one other person to find.
interface Scene {
    ana: Account;
    bob: Account;
    content: string;
    tweetId: string;
}

// Where a screen is reached, and the part of the page the touch-target check covers (the whole page by default).
interface Screen {
    name: string;
    open: (page: Page, scene: Scene) => Promise<void>;
    scope?: (page: Page) => Locator;
}

interface LoggedOutScreen {
    name: string;
    open: (page: Page) => Promise<void>;
}

type OpenScreen = (page: Page, createAccount: () => Promise<Account>) => Promise<void>;

test.describe.configure({ timeout: TEST_TIMEOUT_MS });

async function putAsAccount(account: Account, path: string) {
    const response = await account.api.put(path);

    expect(response.status()).toBe(HTTP_STATUS.noContent);
}

async function createScene(createAccount: () => Promise<Account>): Promise<Scene> {
    const ana = await createAccount();
    const bob = await createAccount();
    const content = `layout check from ${ana.user.username}`;
    const tweetId = await postTweet(ana, content);

    await expectFeedToContain(ana, tweetId);
    await putAsAccount(ana, `${SAVED_TWEETS_API_PATH}/${tweetId}`);
    await putAsAccount(ana, `${LIKES_API_PATH}/${tweetId}`);

    const reply = await bob.api.post(
        API_PATHS.replies(tweetId),
        { data: { content: `a reply from ${bob.user.username}` } },
    );

    expect(reply.status()).toBe(HTTP_STATUS.created);

    return {
        ana,
        bob,
        content,
        tweetId,
    };
}

async function openFeed(page: Page, scene: Scene) {
    await openAs(page, scene.ana, APP_PATHS.feed);

    await expect(postWithText(page, scene.content)).toBeVisible();
}

async function openPostDetails(page: Page, scene: Scene) {
    await openAs(page, scene.ana, APP_PATHS.tweet(scene.tweetId));

    await expect(page.getByRole('article', { name: /^Reply by/ })).toBeVisible();
}

async function openProfileEditSheet(page: Page, scene: Scene) {
    await openAs(page, scene.ana, APP_PATHS.user(scene.ana.user.username));
    await page
        .getByRole('button', { name: 'EDIT' })
        .click();

    await expect(page.getByRole('dialog', { name: 'Say who you are.' })).toBeVisible();
}

async function openComposer(page: Page, scene: Scene) {
    await openFeed(page, scene);
    await page
        .getByRole('button', { name: 'Post' })
        .click();

    await expect(page.getByRole('textbox', { name: COMPOSER_PLACEHOLDER })).toBeVisible();
}

async function openRegisterEmailStep(page: Page) {
    await page.goto(APP_PATHS.register);

    await expect(page.getByLabel('email address', { exact: true })).toBeVisible();
}

async function openRegisterUsernameStep(page: Page) {
    await openRegisterEmailStep(page);
    await typeStep(page, 'email address', newUser().email);

    await expect(page.getByLabel('username', { exact: true })).toBeVisible();
}

async function openRegisterPasswordStep(page: Page) {
    await openRegisterUsernameStep(page);
    await typeStep(page, 'username', newUser().username);

    await expect(page.getByLabel('password', { exact: true })).toBeVisible();
}

const LOGGED_OUT_SCREENS: LoggedOutScreen[] = [
    {
        name: 'login',
        open: async (page) => {
            await page.goto(APP_PATHS.login);

            await expect(page.getByLabel('email or username', { exact: true })).toBeVisible();
        },
    },
    {
        name: 'register_email_step',
        open: openRegisterEmailStep,
    },
    {
        name: 'register_username_step',
        open: openRegisterUsernameStep,
    },
    {
        name: 'register_password_step',
        open: openRegisterPasswordStep,
    },
];

const SCREENS: Screen[] = [
    {
        name: 'feed',
        open: openFeed,
    },
    {
        name: 'people_tab',
        open: async (page, scene) => {
            await openAs(page, scene.ana, APP_PATHS.users);

            await expect(page.getByText(scene.bob.user.username, { exact: true })).toBeVisible();
        },
    },
    {
        name: 'own_profile',
        open: async (page, scene) => {
            await openAs(page, scene.ana, APP_PATHS.user(scene.ana.user.username));

            await expect(postWithText(page, scene.content)).toBeVisible();
        },
    },
    {
        name: 'post_details_with_a_reply',
        open: openPostDetails,
    },
    {
        name: 'saved_page',
        open: async (page, scene) => {
            await openAs(page, scene.ana, SAVED_ROUTE);

            await expect(postWithText(page, scene.content)).toBeVisible();
        },
    },
    {
        name: 'liked_page',
        open: async (page, scene) => {
            await openAs(page, scene.ana, LIKED_ROUTE);

            await expect(postWithText(page, scene.content)).toBeVisible();
        },
    },
    {
        name: 'composer_dialog',
        open: openComposer,
        scope: (page) => page.getByRole('dialog'),
    },
    {
        name: 'profile_edit_sheet',
        open: openProfileEditSheet,
        scope: (page) => page.getByRole('dialog'),
    },
];

function defineLayoutTests(
    name: string,
    openScreen: OpenScreen,
    scope?: (page: Page) => Locator,
) {
    test.describe(
        name,
        () => {
            test(
                `should_not_scroll_sideways_on_${name}`,
                async ({ page, createAccount }) => {
                    await openScreen(page, createAccount);

                    await expectNoHorizontalOverflow(page);
                },
            );

            test(
                `should_give_every_control_a_44px_touch_target_on_${name}`,
                async ({ page, createAccount }) => {
                    await openScreen(page, createAccount);

                    await expectTouchTargetsAtLeast44px(page, scope?.(page));
                },
            );
        },
    );
}

for (const screen of LOGGED_OUT_SCREENS) {
    defineLayoutTests(
        screen.name,
        (page) => screen.open(page),
    );
}

for (const screen of SCREENS) {
    defineLayoutTests(
        screen.name,
        async (page, createAccount) => screen.open(page, await createScene(createAccount)),
        screen.scope,
    );
}
