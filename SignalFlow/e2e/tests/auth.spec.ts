import { expect, test } from '@playwright/test';
import { registerNewUser } from '../support/api/auth-api';
import { TEST_PASSWORD } from '../support/constants';
import { newUser } from '../support/data/test-user';
import { ROUTES } from '../support/routes';
import { authAlert, submitAuthForm, submitEmptyAuthForm } from '../support/ui/auth-form';
import { expectFeedPage, signOut, wordmark } from '../support/ui/home-page';
import { stubSessionExpired } from '../support/ui/route-stubs';
import { loginViaUi } from '../support/ui/session';

test('registers a new account and lands on the feed', async ({ page }) => {
    const user = newUser();

    await submitAuthForm(page, 'register', user.email, user.password);

    await expectFeedPage(page);
    await expect(wordmark(page)).toHaveText('SignalFlow');
});

test('logs in with an existing account', async ({ page, request }) => {
    const user = await registerNewUser(request);

    await loginViaUi(page, user);

    await expectFeedPage(page);
});

test('logs out and protects the feed afterwards', async ({ page, request }) => {
    const user = await registerNewUser(request);
    await loginViaUi(page, user);
    await expect(page).toHaveURL(ROUTES.home);

    await signOut(page);

    await expect(page).toHaveURL(ROUTES.login);
    await page.goto(ROUTES.home);
    await expect(page).toHaveURL(ROUTES.login);
});

test('redirects an unauthenticated visitor to login', async ({ page }) => {
    await page.goto(ROUTES.home);

    await expect(page).toHaveURL(ROUTES.login);
});

test('rejects a duplicate registration', async ({ page, request }) => {
    const user = await registerNewUser(request);

    const form = await submitAuthForm(page, 'register', user.email, user.password);

    await expect(authAlert(form)).toHaveText('An account already exists for that email.');
});

test('rejects a login with the wrong password', async ({ page, request }) => {
    const user = await registerNewUser(request);

    const form = await submitAuthForm(page, 'login', user.email, 'WrongPassword123');

    await expect(authAlert(form)).toHaveText("That email and password don't match an account.");
});

test('shows an inline error for a malformed email', async ({ page }) => {
    const form = await submitAuthForm(page, 'register', 'not-an-email', TEST_PASSWORD);

    await expect(authAlert(form)).toHaveText('Enter a valid email address.');
});

test('shows an inline error for a short password', async ({ page }) => {
    const form = await submitAuthForm(page, 'register', 'short-password@example.com', 'Short1');

    await expect(authAlert(form)).toHaveText('Password must be 8-72 characters.');
});

test('shows an inline error for a weak password', async ({ page }) => {
    const form = await submitAuthForm(page, 'register', 'weak-password@example.com', 'alllowercase');

    await expect(authAlert(form)).toHaveText(
        'Password needs an uppercase letter, a lowercase letter, and a number.',
    );
});

test('shows an inline error when the fields are empty', async ({ page }) => {
    const form = await submitEmptyAuthForm(page, 'register');

    await expect(authAlert(form)).toHaveText('Enter your email.');
});

test('preserves authenticated session across page reload', async ({ page, request }) => {
    const user = await registerNewUser(request);
    await loginViaUi(page, user);
    await expectFeedPage(page);

    // The reload must re-authenticate through the auth check without bouncing to the login page.
    await page.reload();

    await expectFeedPage(page);
    await expect(wordmark(page)).toHaveText('SignalFlow');
});

test('redirects authenticated user away from login and register pages', async ({ page, request }) => {
    const user = await registerNewUser(request);
    await loginViaUi(page, user);
    await expect(page).toHaveURL(ROUTES.home);

    await page.goto(ROUTES.login);
    await expect(page).toHaveURL(ROUTES.home);

    await page.goto(ROUTES.register);
    await expect(page).toHaveURL(ROUTES.home);
});

test('redirects to login when session token expires (401 on auth check)', async ({ page, request }) => {
    const user = await registerNewUser(request);
    await loginViaUi(page, user);
    await expectFeedPage(page);

    await stubSessionExpired(page);

    // On reload the auth check fails with 401 and the app bounces back to the login page.
    await page.reload();

    await expect(page).toHaveURL(ROUTES.login);
});
