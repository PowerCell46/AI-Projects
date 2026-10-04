import { useLayoutEffect, useState } from 'react';
import type { RefObject } from 'react';
import { tabButtonId } from '../../../../utils/tabs';
import type { TabId } from '../../../../utils/tabs';


export interface IndicatorBox {
    left: number;
    width: number;
}

function measureTab(row: HTMLElement, tab: TabId): IndicatorBox | null {
    const tabButton = row.querySelector<HTMLElement>(`#${tabButtonId(tab)}`);

    if (!tabButton) {
        return null;
    }

    return {
        left: tabButton.offsetLeft,
        width: tabButton.offsetWidth,
    };
}

// Where the indicator sits: the active tab's own offset and width. Measured when the tab changes, when the window is
// resized and once the webfonts are in, because the labels get wider or narrower when the fallback font is replaced.
export function useTabIndicator(activeTab: TabId, rowRef: RefObject<HTMLElement | null>): IndicatorBox | null {
    const [indicatorBox, setIndicatorBox] = useState<IndicatorBox | null>(null);

    useLayoutEffect(() => {
        const row = rowRef.current;
        let isCancelled = false;

        function measure() {
            if (row) {
                setIndicatorBox(measureTab(row, activeTab));
            }
        }

        // oxlint-disable-next-line react/set-state-in-effect
        measure();
        window.addEventListener('resize', measure);

        document.fonts?.ready
            .then(() => {
                if (!isCancelled) {
                    measure();
                }
            });

        return () => {
            isCancelled = true;
            window.removeEventListener('resize', measure);
        };
    }, [activeTab, rowRef]);

    return indicatorBox;
}
