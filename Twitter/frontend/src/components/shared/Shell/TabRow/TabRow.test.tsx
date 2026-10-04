import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { tabButtonId, tabPanelId } from '../../../../utils/tabs';
import type { TabId } from '../../../../utils/tabs';
import TabRow from './TabRow';


interface TabBox {
    left: number;
    width: number;
}

const TABS_WIDE: Record<TabId, TabBox> = {
    tweets: {
        left: 0,
        width: 80,
    },
    people: {
        left: 80,
        width: 70,
    },
};

const TABS_AFTER_FONTS: Record<TabId, TabBox> = {
    tweets: {
        left: 0,
        width: 96,
    },
    people: {
        left: 96,
        width: 88,
    },
};

const originalOffsetLeft = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'offsetLeft');
const originalOffsetWidth = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'offsetWidth');

let boxes: Record<TabId, TabBox>;

function stubTabBoxes() {
    function boxOf(element: HTMLElement): TabBox {
        const tab = element.id === tabButtonId('people') ? 'people' : 'tweets';

        return boxes[tab];
    }

    Object.defineProperty(HTMLElement.prototype, 'offsetLeft', {
        configurable: true,
        get() {
            return boxOf(this).left;
        },
    });
    Object.defineProperty(HTMLElement.prototype, 'offsetWidth', {
        configurable: true,
        get() {
            return boxOf(this).width;
        },
    });
}

function restoreTabBoxes() {
    if (originalOffsetLeft) {
        Object.defineProperty(HTMLElement.prototype, 'offsetLeft', originalOffsetLeft);
    }

    if (originalOffsetWidth) {
        Object.defineProperty(HTMLElement.prototype, 'offsetWidth', originalOffsetWidth);
    }
}

function indicator(): HTMLElement {
    const indicatorElement = screen
        .getByRole('tablist')
        .querySelector<HTMLElement>('.tab-row-indicator');

    if (!indicatorElement) {
        throw new Error('Expected the tab row to draw its indicator.');
    }

    return indicatorElement;
}

function renderTabRow(activeTab: TabId, onSelect = vi.fn()) {
    const view = render(<TabRow activeTab={activeTab} onSelect={onSelect} />);

    return {
        ...view,
        onSelect,
        rerenderWith: (nextTab: TabId) => view.rerender(<TabRow activeTab={nextTab} onSelect={onSelect} />),
    };
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
    user = userEvent.setup();
    boxes = TABS_WIDE;
    stubTabBoxes();
});

afterEach(() => {
    restoreTabBoxes();
    Reflect.deleteProperty(document, 'fonts');
});

describe('the tab row markup', () => {
    it('should_be_a_tablist_with_tweets_before_people', () => {
        renderTabRow('tweets');

        const tabs = screen.getAllByRole('tab');

        expect(screen.getByRole('tablist')).toBeTruthy();
        expect(tabs.map((tab) => tab.textContent)).toEqual(['TWEETS', 'PEOPLE']);
    });

    it('should_mark_only_the_active_tab_as_selected', () => {
        renderTabRow('people');

        expect(screen.getByRole('tab', { name: 'TWEETS' }).getAttribute('aria-selected')).toBe('false');
        expect(screen.getByRole('tab', { name: 'PEOPLE' }).getAttribute('aria-selected')).toBe('true');
    });

    it('should_point_each_tab_at_its_panel_with_matching_ids', () => {
        renderTabRow('tweets');

        const tweetsTab = screen.getByRole('tab', { name: 'TWEETS' });

        expect(tweetsTab.id).toBe(tabButtonId('tweets'));
        expect(tweetsTab.getAttribute('aria-controls')).toBe(tabPanelId('tweets'));
    });

    it('should_let_only_the_active_tab_take_focus_with_the_tab_key', () => {
        renderTabRow('people');

        expect(screen.getByRole('tab', { name: 'TWEETS' }).tabIndex).toBe(-1);
        expect(screen.getByRole('tab', { name: 'PEOPLE' }).tabIndex).toBe(0);
    });

    it('should_give_the_tablist_a_name', () => {
        renderTabRow('tweets');

        expect(screen.getByRole('tablist', { name: 'Sections' })).toBeTruthy();
    });

    it('should_make_every_tab_a_plain_button_that_does_not_submit', () => {
        renderTabRow('tweets');

        screen.getAllByRole('tab').forEach((tab) => {
            expect(tab.getAttribute('type')).toBe('button');
        });
    });
});

describe('choosing a tab', () => {
    it('should_report_the_tab_when_an_inactive_tab_is_clicked', async () => {
        const { onSelect } = renderTabRow('tweets');

        await user.click(screen.getByRole('tab', { name: 'PEOPLE' }));

        expect(onSelect).toHaveBeenCalledExactlyOnceWith('people');
    });

    it('should_do_nothing_when_the_active_tab_is_clicked', async () => {
        const { onSelect } = renderTabRow('tweets');

        await user.click(screen.getByRole('tab', { name: 'TWEETS' }));

        expect(onSelect).not.toHaveBeenCalled();
    });

    it('should_switch_to_people_and_focus_it_when_right_arrow_is_pressed_on_tweets', async () => {
        const { onSelect } = renderTabRow('tweets');
        screen.getByRole('tab', { name: 'TWEETS' }).focus();

        await user.keyboard('{ArrowRight}');

        expect(onSelect).toHaveBeenCalledExactlyOnceWith('people');
        expect(document.activeElement).toBe(screen.getByRole('tab', { name: 'PEOPLE' }));
    });

    it('should_wrap_to_tweets_when_right_arrow_is_pressed_on_the_last_tab', async () => {
        const { onSelect } = renderTabRow('people');
        screen.getByRole('tab', { name: 'PEOPLE' }).focus();

        await user.keyboard('{ArrowRight}');

        expect(onSelect).toHaveBeenCalledExactlyOnceWith('tweets');
        expect(document.activeElement).toBe(screen.getByRole('tab', { name: 'TWEETS' }));
    });

    it('should_wrap_to_people_when_left_arrow_is_pressed_on_the_first_tab', async () => {
        const { onSelect } = renderTabRow('tweets');
        screen.getByRole('tab', { name: 'TWEETS' }).focus();

        await user.keyboard('{ArrowLeft}');

        expect(onSelect).toHaveBeenCalledExactlyOnceWith('people');
        expect(document.activeElement).toBe(screen.getByRole('tab', { name: 'PEOPLE' }));
    });

    it('should_move_back_to_tweets_when_left_arrow_is_pressed_on_people', async () => {
        const { onSelect } = renderTabRow('people');
        screen.getByRole('tab', { name: 'PEOPLE' }).focus();

        await user.keyboard('{ArrowLeft}');

        expect(onSelect).toHaveBeenCalledExactlyOnceWith('tweets');
    });

    it('should_ignore_other_keys', async () => {
        const { onSelect } = renderTabRow('tweets');
        screen.getByRole('tab', { name: 'TWEETS' }).focus();

        await user.keyboard('{ArrowDown}a{Enter}');

        expect(onSelect).not.toHaveBeenCalled();
    });
});

describe('the indicator', () => {
    it('should_sit_under_the_active_tab_with_its_offset_and_width', () => {
        renderTabRow('tweets');

        expect(indicator().style.transform).toBe('translateX(0px)');
        expect(indicator().style.width).toBe('80px');
    });

    it('should_move_and_resize_to_the_new_tab_when_the_active_tab_changes', () => {
        const { rerenderWith } = renderTabRow('tweets');

        rerenderWith('people');

        expect(indicator().style.transform).toBe('translateX(80px)');
        expect(indicator().style.width).toBe('70px');
    });

    it('should_be_hidden_from_assistive_technology', () => {
        renderTabRow('tweets');

        expect(indicator().getAttribute('aria-hidden')).toBe('true');
    });

    it('should_be_measured_again_when_the_window_is_resized', () => {
        renderTabRow('tweets');
        boxes = TABS_AFTER_FONTS;

        act(() => {
            window.dispatchEvent(new Event('resize'));
        });

        expect(indicator().style.transform).toBe('translateX(0px)');
        expect(indicator().style.width).toBe('96px');
    });

    it('should_stop_listening_to_the_window_when_the_row_is_removed', () => {
        const removeListener = vi.spyOn(window, 'removeEventListener');
        const { unmount } = renderTabRow('tweets');

        unmount();

        expect(removeListener).toHaveBeenCalledWith('resize', expect.any(Function));
    });

    it('should_be_measured_again_when_the_fonts_have_loaded', async () => {
        let finishLoadingFonts: () => void = () => {};
        Object.defineProperty(document, 'fonts', {
            configurable: true,
            value: {
                ready: new Promise<void>((resolve) => {
                    finishLoadingFonts = resolve;
                }),
            },
        });
        renderTabRow('tweets');
        expect(indicator().style.width).toBe('80px');
        boxes = TABS_AFTER_FONTS;

        await act(async () => {
            finishLoadingFonts();
        });

        expect(indicator().style.width).toBe('96px');
    });

    it('should_not_measure_after_the_row_is_removed_when_the_fonts_load_late', async () => {
        let finishLoadingFonts: () => void = () => {};
        Object.defineProperty(document, 'fonts', {
            configurable: true,
            value: {
                ready: new Promise<void>((resolve) => {
                    finishLoadingFonts = resolve;
                }),
            },
        });
        const { unmount } = renderTabRow('tweets');
        unmount();

        await act(async () => {
            finishLoadingFonts();
        });

        expect(screen.queryByRole('tablist')).toBeNull();
    });

    it('should_work_when_the_browser_has_no_font_loading_api', () => {
        renderTabRow('tweets');

        expect(indicator().style.width).toBe('80px');
    });
});
