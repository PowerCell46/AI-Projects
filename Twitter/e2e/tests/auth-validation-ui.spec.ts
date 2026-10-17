import { expect, type Page } from '@playwright/test';
import { API_PATHS, APP_PATHS, newUser, test, typeStep } from './fixtures';

const EMAIL_QUESTION = 'Where do we reach you?';

const USERNAME_QUESTION = "Pick the name you'll be known by.";

const PASSWORD_QUESTION = 'Now seal it.';

const EMPTY_FIELD_MESSAGE = 'NO VALUE ENTERED — HOLDING AT DEPTH';

const EMAIL_MESSAGE = 'EMAIL MUST BE A VALID EMAIL ADDRESS';

const USERNAME_MESSAGE = 'USERNAME MUST BE 3 TO 15 LETTERS, DIGITS OR UNDERSCORES';

const PASSWORD_LENGTH_MESSAGE = 'PASSWORD MUST BE 8 TO 72 CHARACTERS';

const PASSWORD_COMPOSITION_MESSAGE = 'PASSWORD MUST CONTAIN A LOWERCASE LETTER, AN UPPERCASE LETTER AND A DIGIT';

const TOO_LONG_USERNAME = 'a'.repeat(16);

interface RejectedValue {
    caseName: string;
    value: string;
    message: string;
}

const REJECTED_EMAILS: RejectedValue[] = [
    {
        caseName: 'it_has_no_at_sign',
        value: 'plain-address.example.com',
        message: EMAIL_MESSAGE,
    },
    {
        caseName: 'it_has_no_top_level_domain',
        value: 'someone@example',
        message: EMAIL_MESSAGE,
    },
    {
        caseName: 'it_is_only_spaces',
        value: '   ',
        message: EMPTY_FIELD_MESSAGE,
    },
];

const REJECTED_USERNAMES: RejectedValue[] = [
    {
        caseName: 'it_has_2_characters',
        value: 'ab',
        message: USERNAME_MESSAGE,
    },
    {
        caseName: 'it_has_16_characters',
        value: TOO_LONG_USERNAME,
        message: USERNAME_MESSAGE,
    },
    {
        caseName: 'it_has_a_dash',
        value: 'bad-name',
        message: USERNAME_MESSAGE,
    },
];

const REJECTED_PASSWORDS: RejectedValue[] = [
    {
        caseName: 'it_has_7_characters',
        value: 'Aa1xxxx',
        message: PASSWORD_LENGTH_MESSAGE,
    },
    {
        caseName: 'it_has_no_digit',
        value: 'Password-eee',
        message: PASSWORD_COMPOSITION_MESSAGE,
    },
    {
        caseName: 'it_has_no_uppercase_letter',
        value: 'passw0rd-e2e',
        message: PASSWORD_COMPOSITION_MESSAGE,
    },
];

// The browser's own request log, so a test can prove the form refused the value before it asked the server.
function recordRegisterRequests(page: Page): string[] {
    const methods: string[] = [];

    page.on(
        'request',
        (request) => {
            if (request.url().includes(API_PATHS.register)) {
                methods.push(request.method());
            }
        },
    );

    return methods;
}

async function reachUsernameStep(page: Page, email: string) {
    await page.goto(APP_PATHS.register);
    await typeStep(page, 'email address', email);

    await expect(page.getByRole('heading', { name: USERNAME_QUESTION })).toBeVisible();
}

async function reachPasswordStep(page: Page, email: string, username: string) {
    await reachUsernameStep(page, email);
    await typeStep(page, 'username', username);

    await expect(page.getByRole('heading', { name: PASSWORD_QUESTION })).toBeVisible();
}

test.describe(
    'register validation in the form',
    () => {
        for (const rejected of REJECTED_EMAILS) {
            test(
                `should_stay_on_the_email_step_and_show_the_message_when_${rejected.caseName}`,
                async ({ page }) => {
                    const registerRequests = recordRegisterRequests(page);
                    await page.goto(APP_PATHS.register);

                    await typeStep(page, 'email address', rejected.value);

                    await expect(page.getByRole('alert')).toHaveText(rejected.message);
                    await expect(page.getByRole('heading', { name: EMAIL_QUESTION })).toBeVisible();
                    expect(registerRequests).toEqual([]);
                },
            );
        }

        for (const rejected of REJECTED_USERNAMES) {
            test(
                `should_stay_on_the_username_step_and_show_the_message_when_${rejected.caseName}`,
                async ({ page }) => {
                    const registerRequests = recordRegisterRequests(page);
                    await reachUsernameStep(page, newUser().email);

                    await typeStep(page, 'username', rejected.value);

                    await expect(page.getByRole('alert')).toHaveText(rejected.message);
                    await expect(page.getByRole('heading', { name: USERNAME_QUESTION })).toBeVisible();
                    expect(registerRequests).toEqual([]);
                },
            );
        }

        for (const rejected of REJECTED_PASSWORDS) {
            test(
                `should_stay_on_the_password_step_and_show_the_message_when_${rejected.caseName}`,
                async ({ page }) => {
                    const user = newUser();
                    const registerRequests = recordRegisterRequests(page);
                    await reachPasswordStep(page, user.email, user.username);

                    await typeStep(page, 'password', rejected.value);

                    await expect(page.getByRole('alert')).toHaveText(rejected.message);
                    await expect(page.getByRole('heading', { name: PASSWORD_QUESTION })).toBeVisible();
                    expect(registerRequests).toEqual([]);
                },
            );
        }

        test(
            'should_clear_the_message_when_the_user_types_after_a_refused_value',
            async ({ page }) => {
                await page.goto(APP_PATHS.register);
                await typeStep(page, 'email address', 'plain-address.example.com');
                await expect(page.getByRole('alert')).toHaveText(EMAIL_MESSAGE);

                await page
                    .getByLabel('email address', { exact: true })
                    .fill('plain-address@example.com');

                await expect(page.getByRole('alert')).toHaveText('');
            },
        );

        test(
            'should_go_back_to_the_username_step_with_the_servers_message_when_the_username_is_reserved',
            async ({ page }) => {
                const user = newUser();
                await reachPasswordStep(page, user.email, 'Admin');

                await typeStep(page, 'password', user.password);

                await expect(page.getByRole('heading', { name: USERNAME_QUESTION })).toBeVisible();
                await expect(page.getByRole('alert')).toHaveText('USERNAME IS RESERVED');
            },
        );
    },
);
