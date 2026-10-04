import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import FillIcon from './FillIcon';


function clipPathIdOf(svg: Element): string {
    const id = svg.querySelector('clipPath')?.getAttribute('id');

    if (!id) {
        throw new Error('Expected a clipPath with an id.');
    }

    return id;
}

describe('FillIcon', () => {
    it('should_draw_the_outline_and_the_solid_copy_of_the_same_path', () => {
        const { container } = render(<FillIcon shape="heart" isActive={false} />);

        const paths = container.querySelectorAll('path');

        expect(paths).toHaveLength(2);
        expect(paths[0].getAttribute('d')).toBe(paths[1].getAttribute('d'));
    });

    it('should_use_a_different_path_for_each_shape', () => {
        const heart = render(<FillIcon shape="heart" isActive={false} />).container
            .querySelector('path');
        const bookmark = render(<FillIcon shape="bookmark" isActive={false} />).container
            .querySelector('path');

        expect(heart?.getAttribute('d')).not.toBe(bookmark?.getAttribute('d'));
    });

    it('should_clip_the_solid_copy_with_the_clip_path_of_its_own_icon', () => {
        const { container } = render(<FillIcon shape="heart" isActive={false} />);

        const solid = container.querySelector('.fill-icon-solid');

        expect(solid?.getAttribute('clip-path')).toBe(`url(#${clipPathIdOf(container)})`);
    });

    it('should_give_every_icon_a_unique_clip_path_id_when_many_are_rendered', () => {
        const { container } = render(
            <>
                <FillIcon shape="heart" isActive={false} />
                <FillIcon shape="heart" isActive />
                <FillIcon shape="bookmark" isActive={false} />
            </>,
        );

        const ids = Array.from(container.querySelectorAll('svg'), clipPathIdOf);

        expect(new Set(ids).size).toBe(3);
    });

    it.each([true, false])('should_report_the_active_state_%s_on_the_svg', (isActive) => {
        const { container } = render(<FillIcon shape="bookmark" isActive={isActive} />);

        expect(container.querySelector('svg')?.getAttribute('data-active')).toBe(String(isActive));
    });

    it('should_hide_the_icon_from_assistive_technology', () => {
        const { container } = render(<FillIcon shape="heart" isActive={false} />);

        expect(container.querySelector('svg')?.getAttribute('aria-hidden')).toBe('true');
    });
});
