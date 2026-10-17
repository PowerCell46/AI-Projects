import { expect, test } from '@playwright/test';
import { latestConfirmationUrl, newUser, registerViaApi, TEST_TIMEOUT_MS } from './fixtures';
import { expireConfirmationLinks } from './database-helpers';

test.describe.configure({ timeout: TEST_TIMEOUT_MS });

test('should_show_link_expired_when_the_confirmation_link_is_opened_after_it_expired', async ({ page, request }) => {
    const user = newUser();
    await registerViaApi(request, user);
    const confirmationUrl = await latestConfirmationUrl(user.email);
    expireConfirmationLinks(user.email);

    await page.goto(confirmationUrl);

    await expect(page.getByText('LINK EXPIRED OR ALREADY USED')).toBeVisible();
    await expect(page.getByText('ACCOUNT CONFIRMED')).toHaveCount(0);
});
