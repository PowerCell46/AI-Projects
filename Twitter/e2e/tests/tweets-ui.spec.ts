import { expect, type Locator, type Page } from '@playwright/test';
import {
    APP_PATHS,
    FEED_POLL_TIMEOUT_MS,
    feedTweetIds,
    MAX_TWEET_CHARACTERS,
    MAX_TWEET_IMAGES,
    openAs,
    test,
    TEST_TIMEOUT_MS,
    TEXT_DECLARED_AS_PNG,
    TINY_PNG,
    type UploadedImage,
} from './fixtures';
import { COMPOSER_PLACEHOLDER, expectImageToLoad, firstPost, postWithText } from './page-helpers';

const TOO_MANY_IMAGES_ALERT = `A TWEET CAN HAVE AT MOST ${MAX_TWEET_IMAGES} IMAGES`;

const UNSUPPORTED_IMAGE_ALERT = 'UNSUPPORTED IMAGE TYPE';

const PLAIN_TEXT_FILE: UploadedImage = {
    name: 'notes.txt',
    mimeType: 'text/plain',
    buffer: Buffer.from('plain text'),
};

test.describe.configure({ timeout: TEST_TIMEOUT_MS });

function severalPngs(count: number): UploadedImage[] {
    return Array.from(
        { length: count },
        (_, index) => ({
            ...TINY_PNG,
            name: `dot-${index + 1}.png`,
        }),
    );
}

function composerText(page: Page): Locator {
    return page.getByRole('textbox', { name: COMPOSER_PLACEHOLDER });
}

function publishButton(page: Page): Locator {
    return page.getByRole('button', { name: /^PUBLISH/ });
}

function attachedImages(page: Page): Locator {
    return page.getByRole('img', { name: /^Attached image/ });
}

async function openComposer(page: Page) {
    await page
        .getByRole('button', { name: 'Post' })
        .click();

    await expect(composerText(page)).toBeVisible();
}

async function attach(page: Page, images: UploadedImage[]) {
    await page
        .getByLabel('ATTACH IMAGE')
        .setInputFiles(images);
}

test(
    'should_show_all_four_images_in_the_post_after_a_reload_when_four_images_are_published',
    async ({ page, createAccount }) => {
        const ana = await createAccount();
        const content = `four pictures from ${ana.user.username}`;
        await openAs(page, ana, APP_PATHS.feed);
        await openComposer(page);
        await composerText(page).fill(content);

        await attach(page, severalPngs(MAX_TWEET_IMAGES));

        await expect(attachedImages(page)).toHaveCount(MAX_TWEET_IMAGES);

        await publishButton(page).click();

        await expect(page.getByRole('dialog')).toBeHidden();
        await expect(firstPost(page)).toContainText(content);

        // The feed is filled through Kafka and the outbox, so a reload before then shows an empty feed.
        await expect
            .poll(
                () => feedTweetIds(ana),
                { timeout: FEED_POLL_TIMEOUT_MS },
            )
            .toHaveLength(1);
        await page.reload();

        for (let imageNumber = 1; imageNumber <= MAX_TWEET_IMAGES; imageNumber++) {
            await expectImageToLoad(postWithText(page, content)
                .getByRole('img', { name: `Image ${imageNumber} of ${MAX_TWEET_IMAGES}` }));
        }
    },
);

test(
    'should_keep_the_first_four_images_and_name_the_limit_when_five_images_are_picked',
    async ({ page, createAccount }) => {
        const ana = await createAccount();
        await openAs(page, ana, APP_PATHS.feed);
        await openComposer(page);

        await attach(page, severalPngs(MAX_TWEET_IMAGES + 1));

        await expect(attachedImages(page)).toHaveCount(MAX_TWEET_IMAGES);
        await expect(page.getByText(TOO_MANY_IMAGES_ALERT)).toBeVisible();

        await page
            .getByRole('button', { name: 'Remove image 1' })
            .click();

        await expect(attachedImages(page)).toHaveCount(MAX_TWEET_IMAGES - 1);
        await expect(page.getByText(TOO_MANY_IMAGES_ALERT)).toBeHidden();
    },
);

test(
    'should_refuse_the_file_and_attach_nothing_when_a_plain_text_file_is_picked',
    async ({ page, createAccount }) => {
        const ana = await createAccount();
        await openAs(page, ana, APP_PATHS.feed);
        await openComposer(page);

        await attach(page, [PLAIN_TEXT_FILE]);

        await expect(page.getByText(UNSUPPORTED_IMAGE_ALERT)).toBeVisible();
        await expect(attachedImages(page)).toHaveCount(0);
        await expect(publishButton(page)).toHaveAttribute('aria-disabled', 'true');
    },
);

test(
    'should_show_the_servers_refusal_and_keep_the_draft_when_a_text_file_declared_as_a_png_is_published',
    async ({ page, createAccount }) => {
        const ana = await createAccount();
        await openAs(page, ana, APP_PATHS.feed);
        await openComposer(page);
        await composerText(page).fill('a draft with a fake image');
        await attach(page, [TEXT_DECLARED_AS_PNG]);

        await publishButton(page).click();

        await expect(page.getByRole('alert').filter({ hasText: UNSUPPORTED_IMAGE_ALERT })).toBeVisible();
        await expect(page.getByRole('dialog')).toBeVisible();
        await expect(composerText(page)).toHaveValue('a draft with a fake image');
        await expect(attachedImages(page)).toHaveCount(1);
    },
);

test(
    'should_allow_publishing_at_the_character_limit_and_block_it_one_character_over',
    async ({ page, createAccount }) => {
        const ana = await createAccount();
        await openAs(page, ana, APP_PATHS.feed);
        await openComposer(page);

        await composerText(page).fill('a'.repeat(MAX_TWEET_CHARACTERS));

        await expect(page.getByText(`${MAX_TWEET_CHARACTERS} / ${MAX_TWEET_CHARACTERS}`)).toBeVisible();
        await expect(publishButton(page)).toHaveAttribute('aria-disabled', 'false');

        await composerText(page).fill('a'.repeat(MAX_TWEET_CHARACTERS + 1));

        await expect(page.getByText(`${MAX_TWEET_CHARACTERS + 1} / ${MAX_TWEET_CHARACTERS}`)).toBeVisible();
        await expect(publishButton(page)).toHaveAttribute('aria-disabled', 'true');
    },
);
