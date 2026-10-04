import { useRef } from 'react';
import type { KeyboardEvent } from 'react';
import { TABS, tabButtonId, tabPanelId } from '../../../../utils/tabs';
import type { TabId } from '../../../../utils/tabs';
import { useTabIndicator } from './useTabIndicator';
import './TabRow.css';


function arrowStep(key: string): number {
    switch (key) {
        case 'ArrowRight':
            return 1;

        case 'ArrowLeft':
            return -1;

        default:
            return 0;
    }
}

interface TabRowProps {
    activeTab: TabId;
    onSelect: (tab: TabId) => void;
}

function TabRow({ activeTab, onSelect }: TabRowProps) {
    const rowRef = useRef<HTMLDivElement>(null);
    const tabButtonsRef = useRef<Partial<Record<TabId, HTMLButtonElement | null>>>({});
    const indicatorBox = useTabIndicator(activeTab, rowRef);

    function handleClick(tab: TabId) {
        if (tab !== activeTab) {
            onSelect(tab);
        }
    }

    // An arrow switches at once and wraps round, so the tab that takes the focus is also the one that opens.
    function handleKeyDown(event: KeyboardEvent<HTMLButtonElement>, tab: TabId) {
        const step = arrowStep(event.key);

        if (step === 0) {
            return;
        }

        event.preventDefault();

        const currentIndex = TABS.findIndex((candidate) => candidate.id === tab);
        const nextTab = TABS[(currentIndex + step + TABS.length) % TABS.length].id;

        tabButtonsRef.current[nextTab]?.focus();
        onSelect(nextTab);
    }

    return (
        <div className="tab-row" role="tablist" aria-label="Sections" ref={rowRef}>
            {TABS.map((tab) => (
                <button
                    key={tab.id}
                    id={tabButtonId(tab.id)}
                    ref={(tabButton) => {
                        tabButtonsRef.current[tab.id] = tabButton;
                    }}
                    type="button"
                    role="tab"
                    className="tab-row-tab"
                    aria-selected={tab.id === activeTab}
                    aria-controls={tabPanelId(tab.id)}
                    tabIndex={tab.id === activeTab ? 0 : -1}
                    onClick={() => handleClick(tab.id)}
                    onKeyDown={(event) => handleKeyDown(event, tab.id)}
                >
                    {tab.label}
                </button>
            ))}
            {indicatorBox && (
                <span
                    className="tab-row-indicator"
                    aria-hidden="true"
                    style={{
                        transform: `translateX(${indicatorBox.left}px)`,
                        width: `${indicatorBox.width}px`,
                    }}
                />
            )}
        </div>
    );
}

export default TabRow;
