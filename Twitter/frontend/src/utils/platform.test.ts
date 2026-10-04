import { afterEach, describe, expect, it, vi } from 'vitest';
import { isApplePlatform } from './platform';


afterEach(() => {
    vi.restoreAllMocks();
});

describe('isApplePlatform', () => {
    it.each([
        'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15',
        'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15',
        'Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) AppleWebKit/605.1.15',
    ])('should_recognise_the_apple_agent_%s', (userAgent) => {
        vi.spyOn(navigator, 'userAgent', 'get').mockReturnValue(userAgent);

        expect(isApplePlatform()).toBe(true);
    });

    it.each([
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0 Safari/537.36',
        'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/120.0 Safari/537.36',
        'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36',
    ])('should_not_take_the_agent_%s_for_an_apple_one_just_because_it_says_applewebkit', (userAgent) => {
        vi.spyOn(navigator, 'userAgent', 'get').mockReturnValue(userAgent);

        expect(isApplePlatform()).toBe(false);
    });
});
