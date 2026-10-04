import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { AttachedImage } from '../useImageAttachments';
import ImagePreviews from './ImagePreviews';


function attached(id: number): AttachedImage {
    return {
        id,
        file: new File(
            ['pixels'],
            `picture-${id}.png`,
            { type: 'image/png' },
        ),
        previewUrl: `blob:picture-${id}`,
    };
}

describe('ImagePreviews', () => {
    it('should_render_nothing_when_there_are_no_images', () => {
        const { container } = render(<ImagePreviews images={[]} onRemove={vi.fn()} />);

        expect(container.firstChild).toBeNull();
    });

    it('should_show_each_image_from_its_preview_url_with_a_numbered_alt_text', () => {
        render(<ImagePreviews images={[attached(1), attached(2)]} onRemove={vi.fn()} />);

        const pictures = screen.getAllByRole('img');

        expect(pictures.map((picture) => picture.getAttribute('src'))).toEqual(['blob:picture-1', 'blob:picture-2']);
        expect(pictures.map((picture) => picture.getAttribute('alt')))
            .toEqual(['Attached image 1', 'Attached image 2']);
    });

    it('should_give_each_image_a_labelled_remove_button', () => {
        render(<ImagePreviews images={[attached(1), attached(2)]} onRemove={vi.fn()} />);

        expect(screen.getByRole('button', { name: 'Remove image 1' })).toBeTruthy();
        expect(screen.getByRole('button', { name: 'Remove image 2' })).toBeTruthy();
    });

    it('should_remove_the_image_whose_button_was_pressed', async () => {
        const onRemove = vi.fn();
        render(<ImagePreviews images={[attached(7), attached(9)]} onRemove={onRemove} />);

        await userEvent.click(screen.getByRole('button', { name: 'Remove image 2' }));

        expect(onRemove).toHaveBeenCalledExactlyOnceWith(9);
    });
});
