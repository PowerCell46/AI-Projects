import { expect, test } from '@playwright/test'
import {
    confirmationRowCount,
    latestConfirmationUrl,
    logIn,
    logInAndWaitForFeed,
    newUser,
    registerAndConfirmViaApi,
    registerViaApi,
    typeStep,
} from './fixtures'

test('should_register_confirm_by_the_emailed_link_log_in_by_email_and_reach_the_feed', async ({ page }) => {
    const user = newUser()

    await page.goto('/register')
    await typeStep(page, 'email address', user.email)
    await typeStep(page, 'username', user.username)
    await typeStep(page, 'password', user.password)

    await expect(page.getByRole('heading', { name: 'Check your inbox.' })).toBeVisible()
    await expect(page.getByText(`CONFIRMATION SENT TO ${user.email.toUpperCase()}`)).toBeVisible()

    await page.goto(await latestConfirmationUrl(user.email))

    await expect(page.getByText('ACCOUNT CONFIRMED')).toBeVisible()

    await page.getByRole('link', { name: 'LOG IN' }).click()
    await typeStep(page, 'email or username', user.email)
    await typeStep(page, 'password', user.password)

    await expect(page).toHaveURL(/\/feed$/)
    await expect(page.getByRole('heading', { name: `@${user.username}` })).toBeVisible()
})

test('should_log_in_by_username_keep_the_session_on_reload_and_log_out', async ({ page, request }) => {
    const user = newUser()
    await registerAndConfirmViaApi(request, user)

    await logInAndWaitForFeed(page, user)
    await page.reload()

    await expect(page.getByRole('heading', { name: `@${user.username}` })).toBeVisible()

    await page.getByRole('button', { name: 'LOG OUT' }).click()

    await expect(page).toHaveURL(/\/login$/)
})

test('should_return_to_the_email_step_when_the_email_is_already_registered', async ({ page, request }) => {
    const existingUser = newUser()
    const newcomer = { ...newUser(), email: existingUser.email }
    await registerViaApi(request, existingUser)

    await page.goto('/register')
    await typeStep(page, 'email address', newcomer.email)
    await typeStep(page, 'username', newcomer.username)
    await typeStep(page, 'password', newcomer.password)

    await expect(page.getByRole('heading', { name: 'Where do we reach you?' })).toBeVisible()
    await expect(page.getByRole('alert')).toHaveText('EMAIL ALREADY REGISTERED')
})

test('should_return_to_the_username_step_when_the_username_is_taken', async ({ page, request }) => {
    const existingUser = newUser()
    const newcomer = { ...newUser(), username: existingUser.username }
    await registerViaApi(request, existingUser)

    await page.goto('/register')
    await typeStep(page, 'email address', newcomer.email)
    await typeStep(page, 'username', newcomer.username)
    await typeStep(page, 'password', newcomer.password)

    await expect(page.getByRole('heading', { name: "Pick the name you'll be known by." })).toBeVisible()
    await expect(page.getByRole('alert')).toHaveText('USERNAME ALREADY TAKEN')
})

test('should_rise_on_a_wrong_password_and_clear_the_error_when_typing', async ({ page, request }) => {
    const user = newUser()
    await registerAndConfirmViaApi(request, user)

    await logIn(page, user.username, 'Wr0ngPassword')

    await expect(page.getByRole('alert')).toHaveText('INVALID CREDENTIALS — RISING 420 M')

    await page.getByLabel('password', { exact: true }).fill('x')

    await expect(page.getByRole('alert')).toHaveText('')
})

test('should_offer_a_prefilled_resend_when_an_unconfirmed_user_logs_in', async ({ page, request }) => {
    const user = newUser()
    await registerViaApi(request, user)

    await logIn(page, user.email, user.password)

    await expect(page.getByRole('alert')).toHaveText('PLEASE CONFIRM YOUR EMAIL FIRST')

    await page.getByRole('link', { name: 'RESEND LINK' }).click()

    await expect(page).toHaveURL(/\/resend$/)
    await expect(page.getByLabel('email address', { exact: true })).toHaveValue(user.email)

    await page.getByRole('button', { name: 'SEND LINK' }).click()

    await expect(page.getByText('LINK DISPATCHED')).toBeVisible()
    await expect.poll(() => confirmationRowCount(user.email)).toBe(2)
})

test('should_reject_a_confirmation_link_that_was_already_used', async ({ page, request }) => {
    const user = newUser()
    await registerViaApi(request, user)
    const confirmationUrl = await latestConfirmationUrl(user.email)

    await page.goto(confirmationUrl)

    await expect(page.getByText('ACCOUNT CONFIRMED')).toBeVisible()

    await page.goto(confirmationUrl)

    await expect(page.getByText('LINK EXPIRED OR ALREADY USED')).toBeVisible()
})

test('should_step_back_from_the_password_to_the_username_with_the_browser_back_button', async ({ page }) => {
    const user = newUser()

    await page.goto('/register')
    await typeStep(page, 'email address', user.email)
    await typeStep(page, 'username', user.username)

    await expect(page.getByLabel('password', { exact: true })).toBeVisible()

    await page.goBack()

    await expect(page.getByRole('heading', { name: "Pick the name you'll be known by." })).toBeVisible()
    await expect(page.getByLabel('username', { exact: true })).toHaveValue(user.username)
})

test('should_send_a_logged_out_visitor_from_the_feed_to_login', async ({ page }) => {
    await page.goto('/feed')

    await expect(page).toHaveURL(/\/login$/)
})

test('should_send_a_logged_in_user_from_login_to_the_feed', async ({ page, request }) => {
    const user = newUser()
    await registerAndConfirmViaApi(request, user)
    await logInAndWaitForFeed(page, user)

    await page.goto('/login')

    await expect(page).toHaveURL(/\/feed$/)
})
