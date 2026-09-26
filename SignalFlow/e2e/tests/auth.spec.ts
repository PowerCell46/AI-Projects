import { expect, test } from '@playwright/test'
import { activeAuthForm, loginViaUi, newUser, registerViaApi } from './fixtures'

test('registers a new account and lands on the feed', async ({ page }) => {
    const user = newUser()

    await page.goto('/register')

    const form = activeAuthForm(page)
    await form.getByLabel('Email', { exact: true }).fill(user.email)
    await form.getByLabel('Password', { exact: true }).fill(user.password)
    await form.getByRole('button', { name: 'Create account', exact: true }).click()

    await expect(page).toHaveURL('/')
    await expect(page.locator('.home-wordmark')).toHaveText('signalflow')
    await expect(page.getByRole('heading', { name: 'Feed' })).toBeVisible()
})

test('logs in with an existing account', async ({ page, request }) => {
    const user = newUser()
    await registerViaApi(request, user)

    await loginViaUi(page, user)

    await expect(page).toHaveURL('/')
    await expect(page.getByRole('heading', { name: 'Feed' })).toBeVisible()
})

test('logs out and protects the feed afterwards', async ({ page, request }) => {
    const user = newUser()
    await registerViaApi(request, user)
    await loginViaUi(page, user)
    await expect(page).toHaveURL('/')

    await page.getByRole('button', { name: 'Sign out' }).click()

    await expect(page).toHaveURL('/login')
    await page.goto('/')
    await expect(page).toHaveURL('/login')
})

test('redirects an unauthenticated visitor to login', async ({ page }) => {
    await page.goto('/')

    await expect(page).toHaveURL('/login')
})

test('rejects a duplicate registration', async ({ page, request }) => {
    const user = newUser()
    await registerViaApi(request, user)

    await page.goto('/register')

    const form = activeAuthForm(page)
    await form.getByLabel('Email', { exact: true }).fill(user.email)
    await form.getByLabel('Password', { exact: true }).fill(user.password)
    await form.getByRole('button', { name: 'Create account', exact: true }).click()

    await expect(form.getByRole('alert')).toHaveText('An account already exists for that email.')
})

test('rejects a login with the wrong password', async ({ page, request }) => {
    const user = newUser()
    await registerViaApi(request, user)

    await page.goto('/login')

    const form = activeAuthForm(page)
    await form.getByLabel('Email', { exact: true }).fill(user.email)
    await form.getByLabel('Password', { exact: true }).fill('WrongPassword123')
    await form.getByRole('button', { name: 'Sign in', exact: true }).click()

    await expect(form.getByRole('alert')).toHaveText(
        "That email and password don't match an account.",
    )
})

test('shows an inline error for a malformed email', async ({ page }) => {
    await page.goto('/register')

    const form = activeAuthForm(page)
    await form.getByLabel('Email', { exact: true }).fill('not-an-email')
    await form.getByLabel('Password', { exact: true }).fill('Password123')
    await form.getByRole('button', { name: 'Create account', exact: true }).click()

    await expect(form.getByRole('alert')).toHaveText('Enter a valid email address.')
})

test('shows an inline error for a short password', async ({ page }) => {
    await page.goto('/register')

    const form = activeAuthForm(page)
    await form.getByLabel('Email', { exact: true }).fill('short-password@example.com')
    await form.getByLabel('Password', { exact: true }).fill('Short1')
    await form.getByRole('button', { name: 'Create account', exact: true }).click()

    await expect(form.getByRole('alert')).toHaveText('Password must be 8-72 characters.')
})

test('shows an inline error for a weak password', async ({ page }) => {
    await page.goto('/register')

    const form = activeAuthForm(page)
    await form.getByLabel('Email', { exact: true }).fill('weak-password@example.com')
    await form.getByLabel('Password', { exact: true }).fill('alllowercase')
    await form.getByRole('button', { name: 'Create account', exact: true }).click()

    await expect(form.getByRole('alert')).toHaveText(
        'Password needs an uppercase letter, a lowercase letter, and a number.',
    )
})

test('shows an inline error when the fields are empty', async ({ page }) => {
    await page.goto('/register')

    const form = activeAuthForm(page)
    await form.getByRole('button', { name: 'Create account', exact: true }).click()

    await expect(form.getByRole('alert')).toHaveText('Enter your email.')
})

test('preserves authenticated session across page reload', async ({ page, request }) => {
    const user = newUser()
    await registerViaApi(request, user)
    await loginViaUi(page, user)
    await expect(page).toHaveURL('/')
    await expect(page.getByRole('heading', { name: 'Feed' })).toBeVisible()

    // Reload the page: should re-authenticate via /api/v1/auth/me without bouncing to /login
    await page.reload()
    await expect(page).toHaveURL('/')
    await expect(page.getByRole('heading', { name: 'Feed' })).toBeVisible()
    await expect(page.locator('.home-wordmark')).toHaveText('signalflow')
})

test('redirects authenticated user away from login and register pages', async ({
    page,
    request,
}) => {
    const user = newUser()
    await registerViaApi(request, user)
    await loginViaUi(page, user)
    await expect(page).toHaveURL('/')

    await page.goto('/login')
    await expect(page).toHaveURL('/')

    await page.goto('/register')
    await expect(page).toHaveURL('/')
})

test('redirects to login when session token expires (401 on auth check)', async ({
    page,
    request,
}) => {
    const user = newUser()
    await registerViaApi(request, user)
    await loginViaUi(page, user)
    await expect(page).toHaveURL('/')
    await expect(page.getByRole('heading', { name: 'Feed' })).toBeVisible()

    // Simulate session expiration / invalidated cookie
    await page.route('**/api/v1/auth/me', async (route) => {
        await route.fulfill({
            status: 401,
            contentType: 'application/json',
            body: JSON.stringify({ message: 'Unauthorized' }),
        })
    })

    // On page reload, me() fails with 401 and the app bounces back to /login
    await page.reload()
    await expect(page).toHaveURL('/login')
})


