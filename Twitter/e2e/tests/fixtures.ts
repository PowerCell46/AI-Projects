import { randomUUID } from 'node:crypto';
import { expect, test as base, type APIRequestContext, type Page } from '@playwright/test';

const DEFAULT_MAILPIT_PORT = '8125';

const MAILPIT_URL = `http://127.0.0.1:${process.env.E2E_MAILPIT_PORT ?? DEFAULT_MAILPIT_PORT}`;

const MAILPIT_API_URL = `${MAILPIT_URL}/api/v1`;

const MAILPIT_SEARCH_URL = `${MAILPIT_API_URL}/search`;

const MAILPIT_MESSAGE_URL = `${MAILPIT_API_URL}/message`;

const API_V1 = '/api/v1';

export const API_PATHS = {
    register: `${API_V1}/auth/register`,
    confirm: `${API_V1}/auth/confirm`,
    resendConfirmation: `${API_V1}/auth/confirm/resend`,
    login: `${API_V1}/auth/login`,
    me: `${API_V1}/auth/me`,
    tweets: `${API_V1}/tweets`,
    tweet: (tweetId: string) => `${API_V1}/tweets/${tweetId}`,
    tweetCount: `${API_V1}/tweets/count`,
    tweetDetails: (tweetId: string) => `${API_V1}/tweet-details/${tweetId}`,
    replies: (tweetId: string) => `${API_V1}/tweets/${tweetId}/replies`,
    reply: (tweetId: string, replyId: string) => `${API_V1}/tweets/${tweetId}/replies/${replyId}`,
    like: (tweetId: string) => `${API_V1}/likes/${tweetId}`,
    follow: (username: string) => `${API_V1}/users/${username}/follow`,
    followers: (username: string) => `${API_V1}/users/${username}/followers`,
    following: (username: string) => `${API_V1}/users/${username}/following`,
    currentUser: `${API_V1}/users/me`,
    user: (username: string) => `${API_V1}/users/${username}`,
    authorTweets: (authorId: string) => `${API_V1}/author-tweets/${authorId}`,
    feed: `${API_V1}/feed`,
} as const;

export const APP_PATHS = {
    feed: '/feed',
    login: '/login',
    register: '/register',
    users: '/users',
    user: (username: string) => `/users/${username}`,
    tweet: (tweetId: string) => `/tweets/${tweetId}`,
} as const;

export const HTTP_STATUS = {
    ok: 200,
    created: 201,
    accepted: 202,
    noContent: 204,
    badRequest: 400,
    unauthorized: 401,
    forbidden: 403,
    notFound: 404,
    methodNotAllowed: 405,
    contentTooLarge: 413,
    unsupportedMediaType: 415,
} as const;

export const FEED_POLL_TIMEOUT_MS = 30_000;

// Registrations each wait for a confirmation email, and every feed change crosses Kafka and the outbox.
export const TEST_TIMEOUT_MS = 120_000;

// The feed endpoint's default page size: the number of posts the first page holds.
export const FIRST_PAGE_SIZE = 20;

// The feed endpoint refuses a page size above this.
export const MAX_PAGE_SIZE = 100;

// The first email waits for the outbox poll, the Kafka hop and the consumer's first group join.
const EMAIL_POLL_TIMEOUT_MS = 20_000;

const CONFIRMATION_URL_PATTERN = /https?:\/\/\S+/;

export const TEST_PASSWORD = 'Passw0rd-e2e';

const USERNAME_SUFFIX_LENGTH = 8;

export interface TestUser {
    email: string;
    username: string;
    password: string;
}

// A confirmed user with an API client that holds their session cookie.
export interface Account {
    user: TestUser;
    api: APIRequestContext;
}

interface CreatedTweet {
    id: string;
}

export interface UploadedImage {
    name: string;
    mimeType: string;
    buffer: Buffer;
}

interface FeedPageIds {
    items: CreatedTweet[];
}

interface AccountFixtures {
    createAccount: () => Promise<Account>;
}

interface MailpitMessageSummary {
    ID: string;
}

interface MailpitSearchResponse {
    messages: MailpitMessageSummary[];
}

interface MailpitMessage {
    Text: string;
}

// A real 1x1 PNG, because the tweet service checks the magic bytes and not only the declared type.
export const TINY_PNG: UploadedImage = {
    name: 'dot.png',
    mimeType: 'image/png',
    buffer: Buffer.from(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
        'base64',
    ),
};

// The tweet service decides the type from the leading bytes, so plain text with an image name and type is refused.
export const TEXT_DECLARED_AS_PNG: UploadedImage = {
    name: 'not-an-image.png',
    mimeType: 'image/png',
    buffer: Buffer.from('this is plain text, not a picture'),
};

// A tweet holds at most this many characters (Unicode code points of the trimmed text).
export const MAX_TWEET_CHARACTERS = 280;

// A tweet holds at most this many images.
export const MAX_TWEET_IMAGES = 4;

// Usernames are 3-15 letters, digits or underscores, so "e2e_" plus 8 hex characters.
export function newUser(): TestUser {
    const suffix = randomUUID()
        .replaceAll('-', '')
        .slice(0, USERNAME_SUFFIX_LENGTH);

    return {
        email: `e2e_${suffix}@example.com`,
        username: `e2e_${suffix}`,
        password: TEST_PASSWORD,
    };
}

// Newest first, as Mailpit lists them.
async function searchEmails(searchQuery: string): Promise<MailpitMessageSummary[]> {
    const query = encodeURIComponent(searchQuery);
    const response = await fetch(`${MAILPIT_SEARCH_URL}?query=${query}`);
    const searchResponse: MailpitSearchResponse = await response.json();

    return searchResponse.messages;
}

async function searchEmailsTo(email: string): Promise<MailpitMessageSummary[]> {
    return searchEmails(`to:"${email}"`);
}

async function readMessageText(messageId: string): Promise<string> {
    const response = await fetch(`${MAILPIT_MESSAGE_URL}/${messageId}`);
    const message: MailpitMessage = await response.json();

    return message.Text;
}

async function readConfirmationUrl(messageId: string): Promise<string> {
    const confirmationUrlMatch = CONFIRMATION_URL_PATTERN.exec(await readMessageText(messageId));

    if (confirmationUrlMatch === null) {
        throw new Error('The confirmation email has no link in its text part.');
    }

    return confirmationUrlMatch[0];
}

async function waitForEmails(searchQuery: string, minimumEmailCount: number): Promise<MailpitMessageSummary[]> {
    let emails: MailpitMessageSummary[] = [];

    await expect
        .poll(
            async () => {
                emails = await searchEmails(searchQuery);

                return emails.length;
            },
            { timeout: EMAIL_POLL_TIMEOUT_MS },
        )
        .toBeGreaterThanOrEqual(minimumEmailCount);

    return emails;
}

// The link in the newest confirmation email sent to this address. Waits until enough emails have arrived.
export async function latestConfirmationUrl(email: string, minimumEmailCount = 1): Promise<string> {
    const emails = await waitForEmails(`to:"${email}"`, minimumEmailCount);

    return readConfirmationUrl(emails[0].ID);
}

// The text part of the newest email sent to this address with this subject. Waits until one has arrived.
export async function latestEmailText(email: string, subject: string): Promise<string> {
    const emails = await waitForEmails(`to:"${email}" subject:"${subject}"`, 1);

    return readMessageText(emails[0].ID);
}

export async function confirmationEmailCount(email: string): Promise<number> {
    return (await searchEmailsTo(email)).length;
}

export async function registerViaApi(request: APIRequestContext, user: TestUser) {
    const response = await request.post(
        API_PATHS.register,
        { data: user },
    );

    expect(response.ok()).toBe(true);
}

export async function confirmViaApi(request: APIRequestContext, user: TestUser) {
    const confirmationUrl = new URL(await latestConfirmationUrl(user.email));
    const token = confirmationUrl.searchParams.get('token');
    const response = await request.post(
        API_PATHS.confirm,
        { data: { token } },
    );

    expect(response.ok()).toBe(true);
}

export async function registerAndConfirmViaApi(request: APIRequestContext, user: TestUser) {
    await registerViaApi(request, user);
    await confirmViaApi(request, user);
}

// Each step advances on Enter. A password input is not a textbox, so it is found by its label.
// The form ignores Enter while a step is still sliding in, so the step must be at rest before it is pressed.
export async function typeStep(page: Page, label: string, value: string) {
    await page
        .getByLabel(label, { exact: true })
        .fill(value);

    await expect(page.locator('.step-flow-block')).toHaveAttribute('data-slide', 'idle');

    await page
        .getByLabel(label, { exact: true })
        .press('Enter');
}

export async function logIn(page: Page, identifier: string, password: string) {
    await page.goto(APP_PATHS.login);
    await typeStep(page, 'email or username', identifier);
    await typeStep(page, 'password', password);
}

export async function logInAndWaitForFeed(page: Page, user: TestUser) {
    await logIn(page, user.username, user.password);

    await expect(page).toHaveURL(/\/feed$/);
}

export async function logInViaApi(api: APIRequestContext, user: TestUser) {
    const response = await api.post(
        API_PATHS.login,
        {
            data: {
                identifier: user.username,
                password: user.password,
            },
        },
    );

    expect(response.ok()).toBe(true);
}

// Registers, confirms by the emailed link and logs in, all through the API. Each account gets its own client,
// because the session is a cookie and two users must not share a cookie jar.
export const test = base.extend<AccountFixtures>({
    createAccount: async ({ playwright, baseURL }, use) => {
        const clients: APIRequestContext[] = [];

        await use(async () => {
            const user = newUser();
            const api = await playwright.request.newContext({ baseURL });

            clients.push(api);
            await registerAndConfirmViaApi(api, user);
            await logInViaApi(api, user);

            return {
                user,
                api,
            };
        });

        await Promise.all(clients.map((client) => client.dispose()));
    },
});

export async function postTweet(author: Account, content: string, image?: UploadedImage): Promise<string> {
    const multipart: Record<string, string | UploadedImage> = { content };

    if (image) {
        multipart.images = image;
    }

    const response = await author.api.post(
        API_PATHS.tweets,
        { multipart },
    );

    expect(response.status()).toBe(HTTP_STATUS.created);

    const created: CreatedTweet = await response.json();

    return created.id;
}

export async function deleteTweet(author: Account, tweetId: string) {
    const response = await author.api.delete(API_PATHS.tweet(tweetId));

    expect(response.status()).toBe(HTTP_STATUS.noContent);
}

export async function follow(follower: Account, followee: Account) {
    const response = await follower.api.put(API_PATHS.follow(followee.user.username));

    expect(response.status()).toBe(HTTP_STATUS.noContent);
}

export async function unfollow(follower: Account, followee: Account) {
    const response = await follower.api.delete(API_PATHS.follow(followee.user.username));

    expect(response.status()).toBe(HTTP_STATUS.noContent);
}

export async function feedTweetIds(reader: Account, size = FIRST_PAGE_SIZE): Promise<string[]> {
    const response = await reader.api.get(`${API_PATHS.feed}?size=${size}`);

    expect(response.ok()).toBe(true);

    const feed: FeedPageIds = await response.json();

    return feed.items.map((tweet) => tweet.id);
}

// The feed is filled through Kafka and the outbox, so a test waits for the entry before it looks at the page.
export async function expectFeedToContain(reader: Account, tweetId: string) {
    await expect
        .poll(
            () => feedTweetIds(reader),
            { timeout: FEED_POLL_TIMEOUT_MS },
        )
        .toContain(tweetId);
}

// Hands the account's session cookie to the browser, so a UI test does not type the login each time.
export async function openAs(page: Page, account: Account, path: string) {
    const { cookies } = await account.api.storageState();

    await page
        .context()
        .addCookies(cookies);
    await page.goto(path);
}
