import { fireEvent, render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import Avatar from './Avatar';


const PICTURE_URL = 'http://localhost/api/v1/files/pic-1';

const DEFAULT_PICTURE_URL = '/Default-Profile-Picture.png';

function avatarOf(container: HTMLElement): HTMLImageElement {
    const avatar = container.querySelector('img.avatar');

    if (!(avatar instanceof HTMLImageElement)) {
        throw new Error('The avatar image is missing.');
    }

    return avatar;
}

describe('Avatar', () => {
    it('should_show_the_default_picture_when_there_is_no_picture', () => {
        const { container } = render(<Avatar pictureUrl={null} />);

        expect(avatarOf(container).getAttribute('src')).toBe(DEFAULT_PICTURE_URL);
    });

    it('should_show_the_picture_when_there_is_a_picture', () => {
        const { container } = render(<Avatar pictureUrl={PICTURE_URL} />);

        expect(avatarOf(container).getAttribute('src')).toBe(PICTURE_URL);
    });

    it('should_fall_back_to_the_default_picture_when_the_picture_fails_to_load', () => {
        const { container } = render(<Avatar pictureUrl={PICTURE_URL} />);

        fireEvent.error(avatarOf(container));

        expect(avatarOf(container).getAttribute('src')).toBe(DEFAULT_PICTURE_URL);
    });

    it('should_try_a_new_picture_after_an_earlier_one_failed', () => {
        const { container, rerender } = render(<Avatar pictureUrl={PICTURE_URL} />);
        fireEvent.error(avatarOf(container));

        rerender(<Avatar pictureUrl="http://localhost/api/v1/files/pic-2" />);

        expect(avatarOf(container).getAttribute('src')).toBe('http://localhost/api/v1/files/pic-2');
    });

    it('should_load_the_picture_lazily_and_decode_it_off_the_main_thread', () => {
        const { container } = render(<Avatar pictureUrl={PICTURE_URL} />);

        const avatar = avatarOf(container);

        expect(avatar.getAttribute('loading')).toBe('lazy');
        expect(avatar.getAttribute('decoding')).toBe('async');
    });

    it('should_leave_the_picture_without_alt_text_because_the_username_is_beside_it', () => {
        const { container } = render(<Avatar pictureUrl={PICTURE_URL} />);

        expect(avatarOf(container).getAttribute('alt')).toBe('');
    });

    it('should_be_the_small_size_when_no_size_is_given', () => {
        const { container } = render(<Avatar pictureUrl={null} />);

        expect(avatarOf(container).getAttribute('data-size')).toBe('small');
    });

    it('should_carry_the_large_size_on_the_default_picture_and_on_a_picture', () => {
        const { container } = render(
            <>
                <Avatar pictureUrl={null} size="large" />
                <Avatar pictureUrl={PICTURE_URL} size="large" />
            </>,
        );

        const sizes = Array.from(
            container.querySelectorAll('img.avatar'),
            (avatar) => avatar.getAttribute('data-size'),
        );

        expect(sizes).toEqual(['large', 'large']);
    });
});
