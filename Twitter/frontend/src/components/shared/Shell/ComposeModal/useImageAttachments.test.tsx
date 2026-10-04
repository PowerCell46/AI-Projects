import { act, renderHook } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import {
    IMAGE_TOO_LARGE_MESSAGE,
    MAX_IMAGE_BYTES,
    TOO_MANY_IMAGES_MESSAGE,
    UNSUPPORTED_IMAGE_TYPE_MESSAGE,
} from '../../../../utils/composeChecks';
import { useImageAttachments } from './useImageAttachments';


function picture(name: string, type = 'image/png'): File {
    return new File(
        ['pixels'],
        name,
        { type },
    );
}

function pictureOfSize(name: string, size: number): File {
    const file = picture(name);

    Object.defineProperty(
        file,
        'size',
        { value: size },
    );

    return file;
}

function pictures(count: number): File[] {
    return Array.from(
        { length: count },
        (_, index) => picture(`picture-${index + 1}.png`),
    );
}

describe('addFiles', () => {
    it('should_attach_a_picked_picture_with_a_preview_url', () => {
        const { result } = renderHook(() => useImageAttachments());

        act(() => result.current.addFiles([picture('a.png')]));

        expect(result.current.images).toHaveLength(1);
        expect(result.current.images[0].file.name).toBe('a.png');
        expect(result.current.images[0].previewUrl).toBe('blob:preview-a.png');
        expect(result.current.errorMessage).toBe('');
    });

    it('should_attach_up_to_four_pictures_picked_at_once', () => {
        const { result } = renderHook(() => useImageAttachments());

        act(() => result.current.addFiles(pictures(4)));

        expect(result.current.images).toHaveLength(4);
        expect(result.current.errorMessage).toBe('');
    });

    it('should_refuse_the_fifth_picture_and_keep_the_first_four', () => {
        const { result } = renderHook(() => useImageAttachments());

        act(() => result.current.addFiles(pictures(5)));

        expect(result.current.images).toHaveLength(4);
        expect(result.current.errorMessage).toBe(TOO_MANY_IMAGES_MESSAGE);
    });

    it('should_refuse_a_picture_picked_later_when_four_are_already_attached', () => {
        const { result } = renderHook(() => useImageAttachments());
        act(() => result.current.addFiles(pictures(4)));

        act(() => result.current.addFiles([picture('extra.png')]));

        expect(result.current.images).toHaveLength(4);
        expect(result.current.errorMessage).toBe(TOO_MANY_IMAGES_MESSAGE);
    });

    it('should_refuse_a_picture_of_an_unsupported_type', () => {
        const { result } = renderHook(() => useImageAttachments());

        act(() => result.current.addFiles([picture('a.gif', 'image/gif')]));

        expect(result.current.images).toEqual([]);
        expect(result.current.errorMessage).toBe(UNSUPPORTED_IMAGE_TYPE_MESSAGE);
    });

    it('should_refuse_a_picture_over_the_size_limit', () => {
        const { result } = renderHook(() => useImageAttachments());

        act(() => result.current.addFiles([pictureOfSize('huge.png', MAX_IMAGE_BYTES + 1)]));

        expect(result.current.images).toEqual([]);
        expect(result.current.errorMessage).toBe(IMAGE_TOO_LARGE_MESSAGE);
    });

    it('should_keep_the_pictures_before_a_refused_one_and_skip_the_ones_after_it', () => {
        const { result } = renderHook(() => useImageAttachments());

        act(() => result.current.addFiles([picture('a.png'), picture('b.gif', 'image/gif'), picture('c.png')]));

        expect(result.current.images.map((image) => image.file.name)).toEqual(['a.png']);
        expect(result.current.errorMessage).toBe(UNSUPPORTED_IMAGE_TYPE_MESSAGE);
    });

    it('should_clear_the_error_when_the_next_pick_is_accepted', () => {
        const { result } = renderHook(() => useImageAttachments());
        act(() => result.current.addFiles([picture('a.gif', 'image/gif')]));

        act(() => result.current.addFiles([picture('b.png')]));

        expect(result.current.errorMessage).toBe('');
    });

    it('should_give_every_picture_its_own_id', () => {
        const { result } = renderHook(() => useImageAttachments());

        act(() => result.current.addFiles(pictures(3)));

        expect(new Set(result.current.images.map((image) => image.id)).size).toBe(3);
    });
});

describe('removeImage', () => {
    it('should_drop_the_picture_and_release_its_preview_url', () => {
        const { result } = renderHook(() => useImageAttachments());
        act(() => result.current.addFiles([picture('a.png'), picture('b.png')]));

        act(() => result.current.removeImage(result.current.images[0].id));

        expect(result.current.images.map((image) => image.file.name)).toEqual(['b.png']);
        expect(URL.revokeObjectURL).toHaveBeenCalledExactlyOnceWith('blob:preview-a.png');
    });

    it('should_clear_the_error_when_a_picture_is_removed', () => {
        const { result } = renderHook(() => useImageAttachments());
        act(() => result.current.addFiles(pictures(5)));

        act(() => result.current.removeImage(result.current.images[0].id));

        expect(result.current.errorMessage).toBe('');
    });

    it('should_allow_a_fifth_picture_again_after_one_is_removed', () => {
        const { result } = renderHook(() => useImageAttachments());
        act(() => result.current.addFiles(pictures(4)));
        act(() => result.current.removeImage(result.current.images[0].id));

        act(() => result.current.addFiles([picture('new.png')]));

        expect(result.current.images).toHaveLength(4);
    });

    it('should_do_nothing_when_the_id_is_unknown', () => {
        const { result } = renderHook(() => useImageAttachments());
        act(() => result.current.addFiles([picture('a.png')]));

        act(() => result.current.removeImage(999));

        expect(result.current.images).toHaveLength(1);
        expect(URL.revokeObjectURL).not.toHaveBeenCalled();
    });
});

describe('closing', () => {
    it('should_release_every_remaining_preview_url_when_the_modal_goes_away', () => {
        const { result, unmount } = renderHook(() => useImageAttachments());
        act(() => result.current.addFiles([picture('a.png'), picture('b.png')]));

        unmount();

        expect(vi.mocked(URL.revokeObjectURL).mock.calls.map((call) => call[0]).sort())
            .toEqual(['blob:preview-a.png', 'blob:preview-b.png']);
    });

    it('should_not_release_a_url_twice_when_the_picture_was_removed_before_closing', () => {
        const { result, unmount } = renderHook(() => useImageAttachments());
        act(() => result.current.addFiles([picture('a.png')]));
        act(() => result.current.removeImage(result.current.images[0].id));

        unmount();

        expect(URL.revokeObjectURL).toHaveBeenCalledTimes(1);
    });
});
