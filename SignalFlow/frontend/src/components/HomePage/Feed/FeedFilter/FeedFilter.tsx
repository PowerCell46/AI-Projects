import { useRef } from 'react'
import type { KeyboardEvent } from 'react'
import type { FeedCounts, FeedFilter as FeedFilterValue } from '../../../../api/feed'
import './FeedFilter.css'

interface Tab {
    key: FeedFilterValue
    label: string
}

const TABS: Tab[] = [
    { key: 'ALL', label: 'All' },
    { key: 'SUBSCRIBED', label: 'Subscribed' },
    { key: 'NOT_SUBSCRIBED', label: 'Not subscribed' },
]

interface FeedFilterProps {
    active: FeedFilterValue
    counts: FeedCounts | null
    onChange: (filter: FeedFilterValue) => void
}

function countFor(counts: FeedCounts | null, key: FeedFilterValue): number | null {
    if (!counts) {
        return null
    }

    if (key === 'ALL') {
        return counts.all
    }

    if (key === 'SUBSCRIBED') {
        return counts.subscribed
    }

    return counts.notSubscribed
}

function FeedFilterTabs({ active, counts, onChange }: FeedFilterProps) {
    const tabRefs = useRef<Array<HTMLButtonElement | null>>([])

    function focusTab(index: number) {
        const wrapped = (index + TABS.length) % TABS.length

        tabRefs.current[wrapped]?.focus()
        onChange(TABS[wrapped].key)
    }

    function handleKeyDown(event: KeyboardEvent<HTMLButtonElement>, index: number) {
        if (event.key === 'ArrowRight') {
            event.preventDefault()
            focusTab(index + 1)
        } else if (event.key === 'ArrowLeft') {
            event.preventDefault()
            focusTab(index - 1)
        } else if (event.key === 'Home') {
            event.preventDefault()
            focusTab(0)
        } else if (event.key === 'End') {
            event.preventDefault()
            focusTab(TABS.length - 1)
        }
    }

    return (
        <div className="feed-filter" role="tablist" aria-label="Topic filter">
            {TABS.map((tab, index) => {
                const count = countFor(counts, tab.key)
                const selected = active === tab.key

                return (
                    <button
                        key={tab.key}
                        ref={(node) => {
                            tabRefs.current[index] = node
                        }}
                        type="button"
                        role="tab"
                        aria-selected={selected}
                        tabIndex={selected ? 0 : -1}
                        className="feed-filter-cell"
                        onClick={() => onChange(tab.key)}
                        onKeyDown={(event) => handleKeyDown(event, index)}
                    >
                        {tab.label}
                        {count !== null && <span className="feed-filter-count">{count}</span>}
                    </button>
                )
            })}
        </div>
    )
}

export default FeedFilterTabs
