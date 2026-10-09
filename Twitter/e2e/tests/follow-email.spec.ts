import { expect } from '@playwright/test';
import { follow, latestEmailText, test } from './fixtures';


// Accounts register and confirm through the email link, so the first one also waits for the consumer's group join.
const TEST_TIMEOUT_MS = 120_000;

const FEED_LINK_PATTERN = /https?:\/\/\S+\/feed/;

test.describe.configure({ timeout: TEST_TIMEOUT_MS });

test('should_email_the_followee_with_the_follower_name_and_a_feed_link_when_someone_follows_them', async ({ createAccount }) => {
    const follower = await createAccount();
    const followee = await createAccount();

    await follow(follower, followee);

    const emailText = await latestEmailText(followee.user.email, `${follower.user.username} followed you`);

    expect(emailText).toContain(`Hi ${followee.user.username},`);
    expect(emailText).toContain(`${follower.user.username} (@${follower.user.username}) is now following you.`);
    expect(emailText).toMatch(FEED_LINK_PATTERN);
});
