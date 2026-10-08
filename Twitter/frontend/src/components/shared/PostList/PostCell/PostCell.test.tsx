import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import type { TweetItem } from '../../../../api/tweetPage';
import { ROUTES, profilePath } from '../../../../routes';
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
    likes: 0,
    likedByMe: false,
    replyCount: 0,
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

function renderPost(changes: Partial<TweetItem> = {}, isClickable = true) {
    return render(
        <MemoryRouter>
            <Routes>
                <Route
                    path="/"
                    element={(
                        <PostCell
                            post={{ ...POST, ...changes }}
                            now={NOW}
                            isClickable={isClickable}
                        />
                    )}
                />
                <Route path={ROUTES.tweet} element={<p>details page</p>} />
                <Route path={ROUTES.profile} element={<p>profile page</p>} />
            </Routes>
        </MemoryRouter>,
    );
}

function isOnDetailsPage(): boolean {
    return screen.queryByText('details page') !== null;
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

describe('author link', () => {
    function isOnProfilePage(): boolean {
        return screen.queryByText('profile page') !== null;
    }

    it('should_link_the_avatar_and_the_name_to_the_profile_of_the_author_in_one_link', () => {
        const { container } = renderPost();

        const link = screen.getByRole('link', { name: 'peter_g' });

        expect(link.getAttribute('href')).toBe(profilePath('peter_g'));
        expect(link.querySelector('img.avatar')).not.toBeNull();
        expect(container.querySelectorAll('.post-cell-author a')).toHaveLength(1);
    });

    it('should_open_the_profile_and_not_the_post_when_the_author_is_clicked', async () => {
        renderPost();

        await userEvent.click(screen.getByRole('link', { name: 'peter_g' }));

        expect(isOnProfilePage()).toBe(true);
        expect(isOnDetailsPage()).toBe(false);
    });

    it('should_open_the_profile_when_the_avatar_is_clicked', async () => {
        const { container } = renderPost();
        const avatar = container.querySelector<HTMLElement>('img.avatar');

        if (!avatar) {
            throw new Error('Expected the post to show an avatar.');
        }

        await userEvent.click(avatar);

        expect(isOnProfilePage()).toBe(true);
        expect(isOnDetailsPage()).toBe(false);
    });

    it('should_link_the_author_also_when_the_post_is_not_clickable', () => {
        renderPost({}, false);

        expect(screen.getByRole('link', { name: 'peter_g' }).getAttribute('href')).toBe(profilePath('peter_g'));
    });

    it('should_not_make_the_time_part_of_the_link', () => {
        const { container } = renderPost();

        expect(container.querySelector('a time')).toBeNull();
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

        expect(within(screen.getByText('see https://example.com now')).queryByRole('link')).toBeNull();
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

    it('should_start_the_like_from_the_server_state_when_the_post_loads', () => {
        renderPost({
            views: 99,
            likes: 7,
            likedByMe: true,
        });

        const like = screen.getByRole('button', { name: /^like/i });

        expect(like.getAttribute('aria-pressed')).toBe('true');
        expect(like.textContent).toContain('7');
    });

    it('should_not_show_the_view_count', () => {
        const { container } = renderPost({ views: 4242 });

        expect(container.textContent).not.toContain('4242');
    });
});

describe('opening the details page', () => {
    it('should_open_the_details_page_when_the_text_is_clicked', async () => {
        renderPost();

        await userEvent.click(screen.getByText('hello world'));

        expect(isOnDetailsPage()).toBe(true);
    });

    it('should_not_navigate_when_the_heart_is_clicked', async () => {
        renderPost();

        await userEvent.click(screen.getByRole('button', { name: /^like/i }));

        expect(isOnDetailsPage()).toBe(false);
    });

    it('should_not_navigate_when_the_bookmark_is_clicked', async () => {
        renderPost();

        await userEvent.click(screen.getByRole('button', { name: /^save/i }));

        expect(isOnDetailsPage()).toBe(false);
    });

    it('should_open_the_details_page_once_when_the_reply_link_is_clicked', async () => {
        renderPost();

        await userEvent.click(screen.getByRole('link', { name: /^replies/i }));

        expect(screen.getAllByText('details page')).toHaveLength(1);
    });

    it('should_not_navigate_when_an_image_is_clicked', async () => {
        renderPost({
            images: [
                {
                    id: 'image-1',
                    sizeBytes: 10,
                    contentType: 'image/png',
                },
            ],
        });

        await userEvent.click(screen.getByRole('img', { name: /^image 1/i }));

        expect(isOnDetailsPage()).toBe(false);
    });

    it('should_not_navigate_when_the_click_ends_a_text_selection', () => {
        renderPost();
        const body = screen.getByText('hello world');
        window.getSelection()?.selectAllChildren(body);

        fireEvent.click(body);

        expect(isOnDetailsPage()).toBe(false);

        window.getSelection()?.removeAllRanges();
    });

    it('should_not_be_clickable_when_rendered_on_the_details_page', async () => {
        renderPost({}, false);

        await userEvent.click(screen.getByText('hello world'));

        expect(isOnDetailsPage()).toBe(false);
        expect(screen.getByRole('article').getAttribute('data-clickable')).toBe('false');
    });
});
