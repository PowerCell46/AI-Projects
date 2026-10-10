import { renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { useDocumentTitle } from './useDocumentTitle';


describe('useDocumentTitle', () => {
    it('should_set_the_document_title_and_follow_it_when_it_changes', () => {
        const { rerender } = renderHook(
            ({ title }) => useDocumentTitle(title),
            { initialProps: { title: 'Feed · Twitter' } },
        );

        expect(document.title).toBe('Feed · Twitter');

        rerender({ title: 'People · Twitter' });

        expect(document.title).toBe('People · Twitter');
    });
});
