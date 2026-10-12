import { expect } from '@playwright/test';
import { follow, latestEmailText, test } from './fixtures';


// Accounts register and confirm through the email link, so the first one also waits for the consumer's group join.
const TEST_TIMEOUT_MS = 120_000;

test.describe.configure({ timeout: TEST_TIMEOUT_MS });

test('should_email_the_followee_with_the_follower_name_and_a_profile_link_when_someone_follows_them', async ({ createAccount }) => {
    const follower = await createAccount();
    const followee = await createAccount();

    await follow(follower, followee);

    const emailText = await latestEmailText(followee.user.email, `${follower.user.username} followed you`);
    const profileLinkPattern = new RegExp(`https?://\\S+/users/${follower.user.username}(\\s|$)`);

    expect(emailText).toContain(`Hi ${followee.user.username},`);
    expect(emailText).toContain(`@${follower.user.username} is now following you.`);
    expect(emailText).toMatch(profileLinkPattern);
});
