import { describe, expect, it } from 'vitest';
import { titleOfPath } from './pageTitles';


describe('titleOfPath', () => {
    it.each([
        ['/login', 'Log in · Twitter'],
        ['/register', 'Sign up · Twitter'],
        ['/confirm', 'Confirm your account · Twitter'],
        ['/resend', 'Resend the link · Twitter'],
        ['/feed', 'Feed · Twitter'],
        ['/users', 'People · Twitter'],
        ['/saved', 'Saved tweets · Twitter'],
        ['/liked', 'Liked tweets · Twitter'],
        ['/tweets/0b5f7a3e-1c2d-4e5f-8a9b-0c1d2e3f4a5b', 'Post · Twitter'],
    ])('should_name_%s_%s', (pathname, expectedTitle) => {
        expect(titleOfPath(pathname)).toBe(expectedTitle);
    });

    it('should_name_a_profile_after_its_username', () => {
        expect(titleOfPath('/users/ana_99')).toBe('ana_99 · Twitter');
    });

    it('should_fall_back_to_the_app_name_on_an_unknown_path', () => {
        expect(titleOfPath('/nowhere')).toBe('Twitter');
    });
});
