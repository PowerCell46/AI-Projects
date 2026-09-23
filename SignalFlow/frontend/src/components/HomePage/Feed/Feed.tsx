import { useEffect, useState } from 'react'
import { fetchFeed } from '../../../api/feed'
import type { FeedCounts, FeedFilter as FeedFilterValue, FeedTopic } from '../../../api/feed'
import FeedFilterTabs from './FeedFilter/FeedFilter'
import TopicCard from './TopicCard/TopicCard'
import Pager from './Pager/Pager'
import BackToTop from './BackToTop/BackToTop'
import './Feed.css'

const PAGE_SIZE = 20

type Status = 'loading' | 'ready' | 'error'

function countFor(counts: FeedCounts, filter: FeedFilterValue): number {
    if (filter === 'ALL') {
        return counts.all
    }

    if (filter === 'SUBSCRIBED') {
        return counts.subscribed
    }

    return counts.notSubscribed
}

function Feed() {
    const [filter, setFilter] = useState<FeedFilterValue>('ALL')
    const [items, setItems] = useState<FeedTopic[]>([])
    const [nextCursor, setNextCursor] = useState<string | null>(null)
    const [counts, setCounts] = useState<FeedCounts | null>(null)
    const [status, setStatus] = useState<Status>('loading')
    const [loadingMore, setLoadingMore] = useState(false)
    const [loadMoreError, setLoadMoreError] = useState<string | null>(null)
    const [reloadToken, setReloadToken] = useState(0)

    useEffect(() => {
        let cancelled = false

        setStatus('loading')
        setLoadMoreError(null)

        fetchFeed(filter, null, PAGE_SIZE)
            .then((page) => {
                if (cancelled) {
                    return
                }

                setItems(page.items)
                setNextCursor(page.nextCursor)
                setCounts(page.counts)
                setStatus('ready')
            })
            .catch(() => {
                if (cancelled) {
                    return
                }

                setStatus('error')
            })

        return () => {
            cancelled = true
        }
    }, [filter, reloadToken])

    async function handleLoadMore() {
        if (loadingMore || !nextCursor) {
            return
        }

        setLoadingMore(true)
        setLoadMoreError(null)

        try {
            const page = await fetchFeed(filter, nextCursor, PAGE_SIZE)

            setItems((current) => [...current, ...page.items])
            setNextCursor(page.nextCursor)
            setCounts(page.counts)
        } catch {
            setLoadMoreError("Couldn't load more topics. Try again.")
        } finally {
            setLoadingMore(false)
        }
    }

    function handleCardChange(topicId: string, subscribed: boolean) {
        setItems((current) =>
            current.map((item) => (item.id === topicId ? { ...item, subscribed } : item)),
        )

        setCounts((current) => {
            if (!current) {
                return current
            }

            const delta = subscribed ? 1 : -1

            return {
                all: current.all,
                subscribed: current.subscribed + delta,
                notSubscribed: current.notSubscribed - delta,
            }
        })
    }

    function handleRetry() {
        setReloadToken((current) => current + 1)
    }

    const total = counts ? countFor(counts, filter) : 0
    const isEmpty = status === 'ready' && items.length === 0

    return (
        <div className="feed">
            <div className="feed-column">
                <header className="feed-header">
                    <h1 className="feed-title">Feed</h1>
                    <p className="feed-sub">
                        Subscribe to a topic to route its signals into your feed.
                    </p>

                    <FeedFilterTabs active={filter} counts={counts} onChange={setFilter} />
                </header>

                {status === 'error' && (
                    <div className="feed-state">
                        <p>Couldn't load the feed. Try again.</p>
                        <button type="button" className="feed-retry" onClick={handleRetry}>
                            Retry
                        </button>
                    </div>
                )}

                {status === 'loading' && <p className="feed-state">Loading…</p>}

                {isEmpty && (
                    <p className="feed-state">
                        No topics in this view. Switch to All to find something to follow.
                    </p>
                )}

                {status === 'ready' && items.length > 0 && (
                    <>
                        <div className="feed-list">
                            {items.map((topic) => (
                                <TopicCard key={topic.id} topic={topic} onChange={handleCardChange} />
                            ))}
                        </div>

                        <Pager
                            loaded={items.length}
                            total={total}
                            hasMore={nextCursor !== null}
                            loading={loadingMore}
                            pageSize={PAGE_SIZE}
                            error={loadMoreError}
                            onLoadMore={handleLoadMore}
                        />
                    </>
                )}
            </div>

            <BackToTop />
        </div>
    )
}

export default Feed
