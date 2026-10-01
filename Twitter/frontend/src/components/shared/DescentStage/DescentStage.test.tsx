import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { depthPositionPercent } from '../../../utils/depth';
import DescentStage from './DescentStage';


function renderStage(depthMetres = 140, isAlarm = false) {
    return render(
        <DescentStage depthMetres={depthMetres} seafloorDepthMetres={4900} isAlarm={isAlarm}>
            <p>content</p>
        </DescentStage>,
    );
}

describe('ruler', () => {
    it('should_draw_27_ticks_of_which_7_are_labelled', () => {
        const { container } = renderStage();

        expect(container.querySelectorAll('.ruler-tick')).toHaveLength(27);
        expect(container.querySelectorAll('.ruler-tick[data-major="true"]')).toHaveLength(7);
        expect(container.querySelectorAll('.ruler-tick-label')).toHaveLength(7);
    });

    it('should_label_the_major_ticks_with_the_depth_scale', () => {
        const { container } = renderStage();

        const labels = Array.from(container.querySelectorAll('.ruler-tick-label'))
            .map((label) => label.textContent);

        expect(labels[0]).toBe('0');
        expect(labels[1]).toBe('600');
    });

    it('should_position_each_tick_by_percentage_of_the_stage_height', () => {
        const { container } = renderStage();

        const ticks = container.querySelectorAll<HTMLElement>('.ruler-tick');

        expect(ticks[0].style.top).toBe('6%');
        expect(ticks[26].style.top).toBe('92%');
    });
});

describe('horizon', () => {
    it('should_translate_by_the_percentage_depth_ts_gives_for_the_depth', () => {
        const { container } = renderStage(3860);

        const horizon = container.querySelector<HTMLElement>('.horizon');

        expect(horizon?.style.transform).toBe(`translateY(${depthPositionPercent(3860, 4900)}%)`);
    });

    it('should_show_the_depth_in_the_readout', () => {
        const { container } = renderStage(140);

        expect(container.querySelector('.horizon-readout-number')?.textContent).toBe('140');
        expect(container.querySelector('.horizon-readout-unit')?.textContent).toBe('M');
    });

    it('should_flag_the_alarm_on_the_horizon_and_the_stage', () => {
        const { container } = renderStage(140, true);

        expect(container.querySelector('.horizon')?.getAttribute('data-alarm')).toBe('true');
        expect(container.querySelector('.descent-stage')?.getAttribute('data-alarm')).toBe('true');
    });
});

describe('accessibility', () => {
    it('should_hide_every_gauge_part_from_assistive_technology', () => {
        const { container } = renderStage();

        expect(container.querySelector('.ruler')?.getAttribute('aria-hidden')).toBe('true');
        expect(container.querySelector('.horizon')?.getAttribute('aria-hidden')).toBe('true');
    });

    it('should_render_the_content_in_the_main_landmark_and_the_footer_when_given', () => {
        const { container, getByRole, queryByRole } = renderStage();

        expect(getByRole('main').textContent).toContain('content');
        expect(queryByRole('contentinfo')).toBeNull();
        expect(container.querySelector('footer')).toBeNull();
    });
});
