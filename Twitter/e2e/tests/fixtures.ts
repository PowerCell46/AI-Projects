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
    const response = await request.post('/api/v1/auth/register', { data: user });

    expect(response.ok()).toBe(true);
}

export async function registerAndConfirmViaApi(request: APIRequestContext, user: TestUser) {
    await registerViaApi(request, user);

    const confirmationUrl = new URL(await latestConfirmationUrl(user.email));
    const token = confirmationUrl.searchParams.get('token');
    const response = await request.post('/api/v1/auth/confirm', { data: { token } });

    expect(response.ok()).toBe(true);
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

            return { user, api };
        });

        await Promise.all(clients.map((client) => client.dispose()));
    },
});
