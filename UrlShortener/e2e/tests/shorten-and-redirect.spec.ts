import { randomUUID } from 'node:crypto'
import { expect, test } from '@playwright/test'

function uniqueUrl(): string {
    return `https://example.com/e2e-test?id=${randomUUID()}`
}

async function shortenUrl(page: import('@playwright/test').Page, url: string): Promise<string> {
    await page.goto('/')
    await page.locator('#link-input').fill(url)
    await page.locator('.submit-btn').click()
    await expect(page.locator('.page')).toHaveAttribute('data-phase', 'done')
    const shortUrl = await page.locator('.result-link').textContent()
    if (!shortUrl) {
        throw new Error('No short URL was rendered.')
    }
    return shortUrl.trim()
}

async function submitUrl(page: import('@playwright/test').Page, url: string): Promise<void> {
    await page.goto('/')
    await page.locator('#link-input').fill(url)
    await page.locator('.submit-btn').click()
}

test('shortens a URL and displays the short link', async ({ page }) => {
    const shortUrl = await shortenUrl(page, uniqueUrl())

    expect(shortUrl).toMatch(/^http:\/\/localhost:8081\/\S+$/)
})

test('the short link redirects to the original URL', async ({ page }) => {
    const originalUrl = uniqueUrl()
    const normalizedUrl = new URL(originalUrl).href

    const shortUrl = await shortenUrl(page, originalUrl)

    await page.goto(shortUrl)
    expect(page.url()).toBe(normalizedUrl)
})

test('submitting the same URL twice returns the same short code', async ({ page }) => {
    const url = uniqueUrl()

    const firstShortUrl = await shortenUrl(page, url)
    const secondShortUrl = await shortenUrl(page, url)

    expect(secondShortUrl).toBe(firstShortUrl)
})

test('shows an inline error for invalid input', async ({ page }) => {
    await page.goto('/')
    await page.locator('#link-input').fill('not a url')
    await page.locator('.submit-btn').click()

    await expect(page.locator('.page')).toHaveAttribute('data-phase', 'error')
    await expect(page.locator('.error-msg')).toHaveText("That doesn't look like a web address.")
})

test('does not submit when the input is blank', async ({ page }) => {
    await page.goto('/')
    await page.locator('.submit-btn').click()

    await expect(page.locator('.page')).toHaveAttribute('data-phase', 'idle')
    await expect(page.locator('.error-msg')).toHaveText('')
})

test('shows an error when the URL is over the byte cap', async ({ page }) => {
    const tooLongUrl = `https://example.com/${'a'.repeat(1050)}`

    await submitUrl(page, tooLongUrl)

    await expect(page.locator('.page')).toHaveAttribute('data-phase', 'error')
    await expect(page.locator('.error-msg')).toHaveText("Couldn't reach the server. Try again.")
})

test('shows an error when the URL points at an internal host', async ({ page }) => {
    await submitUrl(page, 'http://169.254.169.254/latest/meta-data')

    await expect(page.locator('.page')).toHaveAttribute('data-phase', 'error')
    await expect(page.locator('.error-msg')).toHaveText("Couldn't reach the server. Try again.")
})

// The frontend's own client-side validation only ever lets an http/https URL through
// (see normalizeUrl in LinkShortener.tsx), so a non-http scheme can't reach the backend
// via a real UI submission. Exercised at the API directly instead.
test('the API rejects a non-http scheme', async ({ request }) => {
    const response = await request.post('http://localhost:8081/api/v1/urls', {
        data: { url: 'ftp://example.com/file' },
    })

    expect(response.status()).toBe(400)
})

test('an unknown short code returns 404', async ({ page }) => {
    const response = await page.goto('http://localhost:8081/does-not-exist-e2e')

    expect(response?.status()).toBe(404)
})
