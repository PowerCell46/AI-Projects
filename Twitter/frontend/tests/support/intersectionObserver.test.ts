import { describe, expect, it, vi } from 'vitest';
import { IntersectionObserverDouble, intersect, observersWatching } from './intersectionObserver';


describe('the IntersectionObserver double', () => {
    it('should_be_what_the_global_name_points_to_in_every_test', () => {
        expect(IntersectionObserver).toBe(IntersectionObserverDouble);
    });

    it('should_record_the_options_it_was_created_with', () => {
        const observer = new IntersectionObserver(
            () => undefined,
            {
                rootMargin: '0px 0px 500px 0px',
                threshold: [0.5],
            },
        );

        expect(observer.rootMargin).toBe('0px 0px 500px 0px');
        expect(observer.thresholds).toEqual([0.5]);
    });

    it('should_tell_the_callback_how_visible_the_target_is_when_a_test_reports_it', async () => {
        const callback = vi.fn();
        const target = document.createElement('div');
        new IntersectionObserver(callback).observe(target);

        await intersect(target, 0.6);

        expect(callback).toHaveBeenCalledOnce();
        expect(callback.mock.calls[0][0][0]).toMatchObject({
            target,
            intersectionRatio: 0.6,
            isIntersecting: true,
        });
    });

    it('should_report_a_target_as_not_intersecting_when_nothing_of_it_is_visible', async () => {
        const callback = vi.fn();
        const target = document.createElement('div');
        new IntersectionObserver(callback).observe(target);

        await intersect(target, 0);

        expect(callback.mock.calls[0][0][0].isIntersecting).toBe(false);
    });

    it('should_not_call_an_observer_that_does_not_watch_the_target', async () => {
        const callback = vi.fn();
        new IntersectionObserver(callback).observe(document.createElement('div'));

        await intersect(document.createElement('div'));

        expect(callback).not.toHaveBeenCalled();
    });

    it('should_stop_calling_an_observer_that_was_disconnected', async () => {
        const callback = vi.fn();
        const target = document.createElement('div');
        const observer = new IntersectionObserver(callback);
        observer.observe(target);
        observer.disconnect();

        await intersect(target);

        expect(callback).not.toHaveBeenCalled();
        expect(observersWatching(target)).toEqual([]);
    });

    it('should_stop_calling_an_observer_that_stopped_watching_the_target', async () => {
        const callback = vi.fn();
        const target = document.createElement('div');
        const observer = new IntersectionObserver(callback);
        observer.observe(target);
        observer.unobserve(target);

        await intersect(target);

        expect(callback).not.toHaveBeenCalled();
    });
});
