import { randomUUID } from 'node:crypto';
import { expect, test as base, type APIRequestContext, type Page } from '@playwright/test';


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

const MAILPIT_URL = 'http://127.0.0.1:8125';

export const FEED_POLL_TIMEOUT_MS = 30_000;

// The feed endpoint's default page size: the number of posts the first page holds.
export const FIRST_PAGE_SIZE = 20;

// The feed endpoint refuses a page size above this.
export const MAX_PAGE_SIZE = 100;

// A real 1x1 PNG, because the tweet service checks the magic bytes and not only the declared type.
export const TINY_PNG: UploadedImage = {
    name: 'dot.png',
    mimeType: 'image/png',
    buffer: Buffer.from(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
        'base64',
    ),
};

// The first email waits for the outbox poll, the Kafka hop and the consumer's first group join.
const EMAIL_POLL_TIMEOUT_MS = 20_000;

const CONFIRMATION_URL_PATTERN = /https?:\/\/\S+/;

export const TEST_PASSWORD = 'Passw0rd-e2e';

// Usernames are 3-15 letters, digits or underscores, so "e2e_" plus 8 hex characters.
export function newUser(): TestUser {
    const suffix = randomUUID()
        .replaceAll('-', '')
        .slice(0, 8);

    return {
        email: `e2e_${suffix}@example.com`,
        username: `e2e_${suffix}`,
        password: TEST_PASSWORD,
    };
}

// Newest first, as Mailpit lists them.
async function searchEmailsTo(email: string): Promise<MailpitMessageSummary[]> {
    const query = encodeURIComponent(`to:"${email}"`);
    const response = await fetch(`${MAILPIT_URL}/api/v1/search?query=${query}`);
    const searchResponse: MailpitSearchResponse = await response.json();

    return searchResponse.messages;
}

async function readConfirmationUrl(messageId: string): Promise<string> {
    const response = await fetch(`${MAILPIT_URL}/api/v1/message/${messageId}`);
    const message: MailpitMessage = await response.json();
    const confirmationUrlMatch = CONFIRMATION_URL_PATTERN.exec(message.Text);

    if (confirmationUrlMatch === null) {
        throw new Error('The confirmation email has no link in its text part.');
    }

    return confirmationUrlMatch[0];
}

// The link in the newest confirmation email sent to this address. Waits until enough emails have arrived.
export async function latestConfirmationUrl(email: string, minimumEmailCount = 1): Promise<string> {
    let emails: MailpitMessageSummary[] = [];

    await expect
        .poll(
            async () => {
                emails = await searchEmailsTo(email);

                return emails.length;
            },
            { timeout: EMAIL_POLL_TIMEOUT_MS },
        )
        .toBeGreaterThanOrEqual(minimumEmailCount);

    return readConfirmationUrl(emails[0].ID);
}

export async function confirmationEmailCount(email: string): Promise<number> {
    return (await searchEmailsTo(email)).length;
}

export async function registerViaApi(request: APIRequestContext, user: TestUser) {
    const response = await request.post(
        '/api/v1/auth/register',
        { data: user },
    );

    expect(response.ok()).toBe(true);
}

export async function confirmViaApi(request: APIRequestContext, user: TestUser) {
    const confirmationUrl = new URL(await latestConfirmationUrl(user.email));
    const token = confirmationUrl.searchParams.get('token');
    const response = await request.post(
        '/api/v1/auth/confirm',
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
    await page.goto('/login');
    await typeStep(page, 'email or username', identifier);
    await typeStep(page, 'password', password);
}

export async function logInAndWaitForFeed(page: Page, user: TestUser) {
    await logIn(page, user.username, user.password);

    await expect(page).toHaveURL(/\/feed$/);
}

export async function logInViaApi(api: APIRequestContext, user: TestUser) {
    const response = await api.post('/api/v1/auth/login', {
        data: {
            identifier: user.username,
            password: user.password,
        },
    });

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
        '/api/v1/tweets',
        { multipart },
    );

    expect(response.status()).toBe(201);

    const created: CreatedTweet = await response.json();

    return created.id;
}

export async function deleteTweet(author: Account, tweetId: string) {
    const response = await author.api.delete(`/api/v1/tweets/${tweetId}`);

    expect(response.status()).toBe(204);
}

export async function follow(follower: Account, followee: Account) {
    const response = await follower.api.put(`/api/v1/users/${followee.user.username}/follow`);

    expect(response.status()).toBe(204);
}

export async function unfollow(follower: Account, followee: Account) {
    const response = await follower.api.delete(`/api/v1/users/${followee.user.username}/follow`);

    expect(response.status()).toBe(204);
}

export async function feedTweetIds(reader: Account, size = FIRST_PAGE_SIZE): Promise<string[]> {
    const response = await reader.api.get(`/api/v1/feed?size=${size}`);

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
