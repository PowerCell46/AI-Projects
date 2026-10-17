import { randomUUID } from 'node:crypto';
import { expect, type APIResponse } from '@playwright/test';
import { API_PATHS, HTTP_STATUS, newUser, test, type TestUser } from './fixtures';

const USERNAME_MIN_LENGTH = 3;

const USERNAME_MAX_LENGTH = 15;

const PASSWORD_MIN_LENGTH = 8;

const PASSWORD_MAX_LENGTH = 72;

const EMAIL_MAX_LENGTH = 254;

const EMAIL_DOMAIN = '@example.com';

const MAX_EMAIL_LOCAL_PART_LENGTH = 64;

const MAX_EMAIL_LABEL_LENGTH = 63;

const MAX_EMAIL_LAST_LABEL_LENGTH = 57;

interface ErrorBody {
    status: number;
    messages: string[];
}

interface RefusedRegistration {
    caseName: string;
    change: Partial<TestUser>;
    message: string;
}

interface AcceptedRegistration {
    caseName: string;
    change: Partial<TestUser>;
}

const EMAIL_MESSAGE = 'email must be a valid email address';

const USERNAME_MESSAGE = 'username must be 3 to 15 letters, digits or underscores';

const PASSWORD_COMPOSITION_MESSAGE = 'password must contain a lowercase letter, an uppercase letter and a digit';

const PASSWORD_SIZE_MESSAGE = `password size must be between ${PASSWORD_MIN_LENGTH} and ${PASSWORD_MAX_LENGTH}`;

// A fresh name of exactly this length, so the boundary cases never collide with each other or with an earlier run.
function usernameOfLength(length: number): string {
    return randomUUID()
        .replaceAll('-', '')
        .slice(0, length);
}

function emailOfLength(length: number): string {
    const localPart = randomUUID()
        .replaceAll('-', '')
        .padEnd(length - EMAIL_DOMAIN.length, 'a')
        .slice(0, length - EMAIL_DOMAIN.length);

    return `${localPart}${EMAIL_DOMAIN}`;
}

// The longest address both rules allow: 254 characters in all, but at most 64 before the @ and 63 per domain label.
function maxLengthEmail(): string {
    const localPart = usernameOfLength(MAX_EMAIL_LOCAL_PART_LENGTH).padEnd(MAX_EMAIL_LOCAL_PART_LENGTH, 'a');
    const domain = [
        'b'.repeat(MAX_EMAIL_LABEL_LENGTH),
        'c'.repeat(MAX_EMAIL_LABEL_LENGTH),
        'd'.repeat(MAX_EMAIL_LAST_LABEL_LENGTH),
        'com',
    ].join('.');

    return `${localPart}@${domain}`;
}

const REFUSED_REGISTRATIONS: RefusedRegistration[] = [
    {
        caseName: 'the_email_has_no_at_sign',
        change: { email: 'plain-address.example.com' },
        message: EMAIL_MESSAGE,
    },
    {
        caseName: 'the_email_has_no_top_level_domain',
        change: { email: 'someone@example' },
        message: EMAIL_MESSAGE,
    },
    {
        caseName: 'the_email_has_a_space',
        change: { email: 'some one@example.com' },
        message: EMAIL_MESSAGE,
    },
    {
        caseName: 'the_email_is_longer_than_254_characters',
        change: { email: emailOfLength(EMAIL_MAX_LENGTH + 1) },
        message: `email size must be between 0 and ${EMAIL_MAX_LENGTH}`,
    },
    {
        caseName: 'the_username_has_2_characters',
        change: { username: usernameOfLength(USERNAME_MIN_LENGTH - 1) },
        message: USERNAME_MESSAGE,
    },
    {
        caseName: 'the_username_has_16_characters',
        change: { username: usernameOfLength(USERNAME_MAX_LENGTH + 1) },
        message: USERNAME_MESSAGE,
    },
    {
        caseName: 'the_username_has_a_dash',
        change: { username: 'bad-name' },
        message: USERNAME_MESSAGE,
    },
    {
        caseName: 'the_username_has_a_space',
        change: { username: 'bad name' },
        message: USERNAME_MESSAGE,
    },
    {
        caseName: 'the_username_has_a_non_ascii_letter',
        change: { username: 'café_e2e' },
        message: USERNAME_MESSAGE,
    },
    {
        caseName: 'the_username_is_reserved',
        change: { username: 'Admin' },
        message: 'username is reserved',
    },
    {
        caseName: 'the_password_has_7_characters',
        change: { password: 'Aa1xxxx' },
        message: PASSWORD_SIZE_MESSAGE,
    },
    {
        caseName: 'the_password_has_73_characters',
        change: { password: `Aa1${'x'.repeat(PASSWORD_MAX_LENGTH - 2)}` },
        message: PASSWORD_SIZE_MESSAGE,
    },
    {
        caseName: 'the_password_has_no_uppercase_letter',
        change: { password: 'passw0rd-e2e' },
        message: PASSWORD_COMPOSITION_MESSAGE,
    },
    {
        caseName: 'the_password_has_no_lowercase_letter',
        change: { password: 'PASSW0RD-E2E' },
        message: PASSWORD_COMPOSITION_MESSAGE,
    },
    {
        caseName: 'the_password_has_no_digit',
        change: { password: 'Password-eee' },
        message: PASSWORD_COMPOSITION_MESSAGE,
    },
    {
        caseName: 'the_email_is_empty',
        change: { email: '' },
        message: 'email must not be blank',
    },
    {
        caseName: 'the_username_is_empty',
        change: { username: '' },
        message: 'username must not be blank',
    },
    {
        caseName: 'the_password_is_empty',
        change: { password: '' },
        message: 'password must not be blank',
    },
    {
        caseName: 'the_username_is_missing',
        change: { username: undefined },
        message: 'username must not be blank',
    },
];

const ACCEPTED_REGISTRATIONS: AcceptedRegistration[] = [
    {
        caseName: 'the_username_has_3_characters',
        change: { username: usernameOfLength(USERNAME_MIN_LENGTH) },
    },
    {
        caseName: 'the_username_has_15_characters',
        change: { username: usernameOfLength(USERNAME_MAX_LENGTH) },
    },
    {
        caseName: 'the_username_has_digits_and_underscores',
        change: { username: `_${usernameOfLength(USERNAME_MIN_LENGTH)}_9` },
    },
    {
        caseName: 'the_password_has_8_characters',
        change: { password: 'Aa1xxxxx' },
    },
    {
        caseName: 'the_password_has_72_characters',
        change: { password: `Aa1${'x'.repeat(PASSWORD_MAX_LENGTH - 3)}` },
    },
    {
        caseName: 'the_email_has_254_characters',
        change: { email: maxLengthEmail() },
    },
];

async function expectRefusal(response: APIResponse, message: string) {
    expect(response.status()).toBe(HTTP_STATUS.badRequest);

    const errorBody: ErrorBody = await response.json();

    expect(errorBody.status).toBe(HTTP_STATUS.badRequest);
    expect(errorBody.messages).toContain(message);
}

test.describe(
    'register validation',
    () => {
        for (const refused of REFUSED_REGISTRATIONS) {
            test(
                `should_refuse_registration_with_400_when_${refused.caseName}`,
                async ({ request }) => {
                    const response = await request.post(
                        API_PATHS.register,
                        {
                            data: {
                                ...newUser(),
                                ...refused.change,
                            },
                        },
                    );

                    await expectRefusal(response, refused.message);
                },
            );
        }

        for (const accepted of ACCEPTED_REGISTRATIONS) {
            test(
                `should_register_with_201_when_${accepted.caseName}`,
                async ({ request }) => {
                    const response = await request.post(
                        API_PATHS.register,
                        {
                            data: {
                                ...newUser(),
                                ...accepted.change,
                            },
                        },
                    );

                    expect(response.status()).toBe(HTTP_STATUS.created);
                },
            );
        }

        test(
            'should_keep_the_email_free_when_a_registration_with_a_bad_username_is_refused',
            async ({ request }) => {
                const user = newUser();
                const refused = await request.post(
                    API_PATHS.register,
                    {
                        data: {
                            ...user,
                            username: 'bad-name',
                        },
                    },
                );

                await expectRefusal(refused, USERNAME_MESSAGE);

                const retried = await request.post(
                    API_PATHS.register,
                    { data: user },
                );

                expect(retried.status()).toBe(HTTP_STATUS.created);
            },
        );

        test(
            'should_refuse_registration_with_400_when_the_body_is_not_json',
            async ({ request }) => {
                const response = await request.post(
                    API_PATHS.register,
                    {
                        headers: { 'content-type': 'application/json' },
                        data: '{not json',
                    },
                );

                await expectRefusal(response, 'Malformed request body.');
            },
        );
    },
);
