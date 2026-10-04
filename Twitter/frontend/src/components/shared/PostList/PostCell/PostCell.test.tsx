import { render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { TweetItem } from '../../../../api/tweetPage';
import PostCell from './PostCell';


vi.mock('../../../../api/likes', () => ({
    likeTweet: vi.fn(),
    unlikeTweet: vi.fn(),
}));

vi.mock('../../../../api/savedTweets', () => ({
    saveTweet: vi.fn(),
    unsaveTweet: vi.fn(),
}));

const NOW = new Date('2026-10-04T12:00:00.000Z');

const POST: TweetItem = {
    id: '6f1c2a3e-0000-4000-8000-000000000001',
    views: 3,
    savedByMe: false,
    content: 'hello world',
    createdAt: '2026-10-04T11:55:00.000Z',
    updatedAt: '2026-10-04T11:55:00.000Z',
    author: {
        id: '6f1c2a3e-0000-4000-8000-0000000000aa',
        username: 'peter_g',
        profilePictureUrl: null,
    },
    images: [],
};

function renderPost(changes: Partial<TweetItem> = {}) {
    return render(<PostCell post={{ ...POST, ...changes }} now={NOW} />);
}

describe('author row', () => {
    it('should_show_the_username_once_and_no_handle', () => {
        const { container } = renderPost();

        expect(within(container).getAllByText('peter_g')).toHaveLength(1);
        expect(container.textContent).not.toContain('@');
    });

    it('should_show_the_age_of_the_post_in_a_time_element_that_carries_the_full_timestamp', () => {
        const { container } = renderPost();

        const time = container.querySelector('time');

        expect(time?.textContent).toBe('5M');
        expect(time?.getAttribute('datetime')).toBe(POST.createdAt);
    });

    it('should_show_the_default_picture_when_the_author_has_no_picture', () => {
        const { container } = renderPost();

        expect(container.querySelector('img.avatar')?.getAttribute('src')).toBe('/Default-Profile-Picture.png');
    });

    it('should_show_the_picture_when_the_author_has_one', () => {
        const { container } = renderPost({
            author: {
                ...POST.author,
                profilePictureUrl: 'http://localhost/api/v1/files/pic-1',
            },
        });

        expect(container.querySelector('img.avatar')?.getAttribute('src')).toBe('http://localhost/api/v1/files/pic-1');
    });

    it('should_name_the_post_after_its_author', () => {
        renderPost();

        expect(screen.getByRole('article', { name: 'Post by peter_g' })).toBeTruthy();
    });
});

describe('body', () => {
    it('should_render_the_content_with_the_line_breaks_kept', () => {
        const { container } = renderPost({ content: 'first line\nsecond line' });

        expect(container.querySelector('.post-cell-body')?.textContent).toBe('first line\nsecond line');
    });

    it('should_let_the_browser_pick_the_text_direction', () => {
        const { container } = renderPost({ content: 'שלום' });

        expect(container.querySelector('.post-cell-body')?.getAttribute('dir')).toBe('auto');
    });

    it('should_strip_the_bidi_controls_but_keep_the_marks_and_the_text', () => {
        const { container } = renderPost({ content: 'a‮b⁦c‎d' });

        expect(container.querySelector('.post-cell-body')?.textContent).toBe('abc‎d');
    });

    it('should_render_links_as_plain_text', () => {
        renderPost({ content: 'see https://example.com now' });

        expect(screen.queryByRole('link')).toBeNull();
        expect(screen.getByText('see https://example.com now')).toBeTruthy();
    });

    it('should_render_the_content_as_text_and_not_as_markup', () => {
        const { container } = renderPost({ content: '<img src=x onerror=alert(1)>' });

        expect(container.querySelector('.post-cell-body img')).toBeNull();
        expect(container.querySelector('.post-cell-body')?.textContent).toBe('<img src=x onerror=alert(1)>');
    });

    it('should_render_no_body_when_the_post_has_only_images', () => {
        const { container } = renderPost({
            content: '',
            images: [
                {
                    id: 'image-1',
                    sizeBytes: 10,
                    contentType: 'image/png',
                },
            ],
        });

        expect(container.querySelector('.post-cell-body')).toBeNull();
        expect(container.querySelector('.post-images')).not.toBeNull();
    });
});

describe('images', () => {
    it('should_render_no_image_grid_when_the_post_has_no_images', () => {
        const { container } = renderPost();

        expect(container.querySelector('.post-images')).toBeNull();
    });

    it('should_render_the_images_between_the_body_and_the_actions', () => {
        const { container } = renderPost({
            images: [
                {
                    id: 'image-1',
                    sizeBytes: 10,
                    contentType: 'image/png',
                },
            ],
        });

        const parts = Array.from(
            container.querySelector('article')?.children ?? [],
            (part) => part.className,
        );

        expect(parts).toEqual(['post-cell-author', 'post-cell-body', 'post-images', 'post-actions']);
    });
});

describe('actions', () => {
    it('should_start_the_bookmark_pressed_when_the_post_is_saved_by_me', () => {
        renderPost({ savedByMe: true });

        expect(screen.getByRole('button', { name: /^save/i }).getAttribute('aria-pressed')).toBe('true');
    });

    it('should_start_the_bookmark_unpressed_when_the_post_is_not_saved_by_me', () => {
        renderPost({ savedByMe: false });

        expect(screen.getByRole('button', { name: /^save/i }).getAttribute('aria-pressed')).toBe('false');
    });

    it('should_start_the_like_unpressed_at_zero_whatever_the_view_count_is', () => {
        renderPost({ views: 99 });

        const like = screen.getByRole('button', { name: /^like/i });

        expect(like.getAttribute('aria-pressed')).toBe('false');
        expect(like.textContent).toContain('0');
    });

    it('should_not_show_the_view_count', () => {
        const { container } = renderPost({ views: 4242 });

        expect(container.textContent).not.toContain('4242');
    });
});
