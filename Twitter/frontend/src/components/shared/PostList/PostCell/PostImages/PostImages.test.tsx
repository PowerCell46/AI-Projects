import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ENDPOINTS } from '../../../../../api/endpoints';
import type { TweetImage } from '../../../../../api/tweetPage';
import PostImages from './PostImages';


const TWEET_ID = '6f1c2a3e-0000-4000-8000-000000000001';

function imagesOf(count: number): TweetImage[] {
    return Array.from({ length: count }, (_, index) => ({
        id: `image-${index + 1}`,
        sizeBytes: 1234,
        contentType: 'image/png',
    }));
}

describe('PostImages', () => {
    it('should_render_nothing_when_the_post_has_no_images', () => {
        const { container } = render(<PostImages tweetId={TWEET_ID} images={[]} />);

        expect(container.firstChild).toBeNull();
    });

    it.each([1, 2, 3, 4])('should_render_%i_pictures_and_mark_the_grid_with_the_count', (count) => {
        const { container } = render(<PostImages tweetId={TWEET_ID} images={imagesOf(count)} />);

        expect(screen.getAllByRole('img')).toHaveLength(count);
        expect(container.querySelector('.post-images')?.getAttribute('data-count')).toBe(String(count));
    });

    it('should_number_the_alt_text_of_each_picture_out_of_the_total', () => {
        render(<PostImages tweetId={TWEET_ID} images={imagesOf(3)} />);

        const alts = screen.getAllByRole('img').map((picture) => picture.getAttribute('alt'));

        expect(alts).toEqual(['Image 1 of 3', 'Image 2 of 3', 'Image 3 of 3']);
    });

    it('should_load_each_picture_from_the_tweet_image_route_of_its_own_tweet', () => {
        render(<PostImages tweetId={TWEET_ID} images={imagesOf(2)} />);

        const sources = screen.getAllByRole('img').map((picture) => picture.getAttribute('src'));

        expect(sources).toEqual([
            ENDPOINTS.tweetImage(TWEET_ID, 'image-1'),
            ENDPOINTS.tweetImage(TWEET_ID, 'image-2'),
        ]);
    });

    it('should_load_the_pictures_lazily', () => {
        render(<PostImages tweetId={TWEET_ID} images={imagesOf(2)} />);

        const modes = screen.getAllByRole('img').map((picture) => picture.getAttribute('loading'));

        expect(modes).toEqual(['lazy', 'lazy']);
    });

    it('should_keep_the_pictures_in_the_order_the_post_lists_them', () => {
        const { container } = render(<PostImages tweetId={TWEET_ID} images={imagesOf(4)} />);

        const sources = Array.from(
            container.querySelectorAll('img'),
            (picture) => picture.getAttribute('src'),
        );

        expect(sources).toEqual(imagesOf(4).map((image) => ENDPOINTS.tweetImage(TWEET_ID, image.id)));
    });

    it('should_not_make_the_pictures_clickable', () => {
        render(<PostImages tweetId={TWEET_ID} images={imagesOf(2)} />);

        expect(screen.queryByRole('link')).toBeNull();
        expect(screen.queryByRole('button')).toBeNull();
    });
});
