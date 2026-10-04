import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { avatarTintOf } from '../../../../../utils/avatar';
import Avatar from './Avatar';


const USER_ID = '6f1c2a3e-0000-4000-8000-000000000001';

describe('Avatar', () => {
    it('should_show_the_first_two_letters_in_capitals_when_there_is_no_picture', () => {
        render(<Avatar userId={USER_ID} username="peter_g" pictureUrl={null} />);

        expect(screen.getByText('PE')).toBeTruthy();
    });

    it('should_pick_the_tint_from_the_user_id_when_there_is_no_picture', () => {
        render(<Avatar userId={USER_ID} username="peter_g" pictureUrl={null} />);

        expect(screen.getByText('PE').getAttribute('data-tint')).toBe(String(avatarTintOf(USER_ID)));
    });

    it('should_give_the_same_tint_to_one_user_in_every_post', () => {
        const { container } = render(
            <>
                <Avatar userId={USER_ID} username="peter_g" pictureUrl={null} />
                <Avatar userId={USER_ID} username="peter_g" pictureUrl={null} />
            </>,
        );

        const tints = Array.from(
            container.querySelectorAll('.avatar'),
            (avatar) => avatar.getAttribute('data-tint'),
        );

        expect(tints[0]).toBe(tints[1]);
    });

    it('should_show_the_picture_and_no_letters_when_there_is_a_picture', () => {
        const { container } = render(
            <Avatar userId={USER_ID} username="peter_g" pictureUrl="http://localhost/api/v1/files/pic-1" />,
        );

        expect(container.querySelector('img')?.getAttribute('src')).toBe('http://localhost/api/v1/files/pic-1');
        expect(screen.queryByText('PE')).toBeNull();
    });

    it('should_leave_the_picture_without_alt_text_because_the_username_is_beside_it', () => {
        const { container } = render(
            <Avatar userId={USER_ID} username="peter_g" pictureUrl="http://localhost/api/v1/files/pic-1" />,
        );

        expect(container.querySelector('img')?.getAttribute('alt')).toBe('');
    });

    it('should_hide_the_letters_from_assistive_technology', () => {
        render(<Avatar userId={USER_ID} username="peter_g" pictureUrl={null} />);

        expect(screen.getByText('PE').getAttribute('aria-hidden')).toBe('true');
    });
});
