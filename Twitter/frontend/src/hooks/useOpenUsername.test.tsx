import { renderHook } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { useActiveTab } from './useActiveTab';
import { useOpenUsername } from './useOpenUsername';
import { ROUTES } from '../routes';


function routerAt(path: string) {
    return function Wrapper({ children }: { children: ReactNode }) {
        return <MemoryRouter initialEntries={[path]}>{children}</MemoryRouter>;
    };
}

describe('useOpenUsername', () => {
    it('should_return_the_username_when_the_address_is_a_profile', () => {
        const { result } = renderHook(useOpenUsername, { wrapper: routerAt('/users/ana_b') });

        expect(result.current).toBe('ana_b');
    });

    it('should_return_the_segment_as_the_address_holds_it_when_it_is_encoded', () => {
        const { result } = renderHook(useOpenUsername, { wrapper: routerAt('/users/a%20b') });

        expect(result.current).toBe('a%20b');
    });

    it.each([
        ROUTES.feed,
        ROUTES.people,
        '/tweets/6f1c2a3e-0000-4000-8000-000000000001',
        '/users/ana/extra',
    ])('should_return_null_when_the_address_is_%s', (path) => {
        const { result } = renderHook(useOpenUsername, { wrapper: routerAt(path) });

        expect(result.current).toBeNull();
    });
});

describe('useActiveTab on a profile address', () => {
    it('should_not_read_a_profile_as_the_people_tab', () => {
        const { result } = renderHook(useActiveTab, { wrapper: routerAt('/users/ana_b') });

        expect(result.current).toBeNull();
    });

    it('should_still_read_the_people_address_as_the_people_tab', () => {
        const { result } = renderHook(useActiveTab, { wrapper: routerAt(ROUTES.people) });

        expect(result.current).toBe('people');
    });
});
