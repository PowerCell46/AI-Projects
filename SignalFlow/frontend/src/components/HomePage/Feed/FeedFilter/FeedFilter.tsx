import { useRef } from 'react';
import type { KeyboardEvent } from 'react';
import type { FeedCounts, FeedFilterValue } from '../../../../api/feed';
import { countForFilter } from '../../../../utils/feedCounts';
import './FeedFilter.css';

interface FilterTab {
    key: FeedFilterValue;
    label: string;
}

const TABS: FilterTab[] = [
    { key: 'ALL', label: 'All' },
    { key: 'SUBSCRIBED', label: 'Subscribed' },
    { key: 'NOT_SUBSCRIBED', label: 'Not subscribed' },
];

function targetTabIndex(key: string, currentIndex: number): number | null {
    if (key === 'ArrowRight') {
        return currentIndex + 1;
    }

    if (key === 'ArrowLeft') {
        return currentIndex - 1;
    }

    if (key === 'Home') {
        return 0;
    }

    if (key === 'End') {
        return TABS.length - 1;
    }

    return null;
}

interface FeedFilterProps {
    active: FeedFilterValue;
    counts: FeedCounts | null;
    onChange: (filter: FeedFilterValue) => void;
}

function FeedFilter({ active, counts, onChange }: FeedFilterProps) {
    const tabRefs = useRef<Array<HTMLButtonElement | null>>([]);

    function focusTab(index: number) {
        const wrappedIndex = (index + TABS.length) % TABS.length;

        tabRefs.current[wrappedIndex]?.focus();
        onChange(TABS[wrappedIndex].key);
    }

    function handleKeyDown(event: KeyboardEvent<HTMLButtonElement>, index: number) {
        const targetIndex = targetTabIndex(event.key, index);

        if (targetIndex === null) {
            return;
        }

        event.preventDefault();
        focusTab(targetIndex);
    }

    return (
        <div className="feed-filter" role="tablist" aria-label="Topic filter">
            {TABS.map((tab, index) => {
                const isSelected = active === tab.key;

                return (
                    <button
                        key={tab.key}
                        type="button"
                        role="tab"
                        className="feed-filter-cell"
                        aria-selected={isSelected}
                        tabIndex={isSelected ? 0 : -1}
                        ref={(tabButton) => {
                            tabRefs.current[index] = tabButton;
                        }}
                        onClick={() => onChange(tab.key)}
                        onKeyDown={(event) => handleKeyDown(event, index)}
                    >
                        {tab.label}
                        {counts && <span className="feed-filter-count">{countForFilter(counts, tab.key)}</span>}
                    </button>
                );
            })}
        </div>
    );
}

export default FeedFilter;
