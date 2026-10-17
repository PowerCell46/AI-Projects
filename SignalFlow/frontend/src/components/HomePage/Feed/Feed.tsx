import { countForFilter } from '../../../utils/feedCounts';
import BackToTop from './BackToTop/BackToTop';
import FeedFilter from './FeedFilter/FeedFilter';
import Pager from './Pager/Pager';
import TopicCard from './TopicCard/TopicCard';
import { PAGE_SIZE, useFeedPages } from './useFeedPages';
import './Feed.css';

function Feed() {
    const feed = useFeedPages();

    const total = feed.counts ? countForFilter(feed.counts, feed.filter) : 0;
    const isEmpty = feed.status === 'ready' && feed.topics.length === 0;
    const hasTopics = feed.status === 'ready' && feed.topics.length > 0;

    return (
        <div className="feed">
            <div className="feed-column">
                <header className="feed-header">
                    <h1 className="feed-title">Feed</h1>
                    <p className="feed-sub">
                        Subscribe to a topic to route its signals into your feed.
                    </p>

                    <FeedFilter active={feed.filter} counts={feed.counts} onChange={feed.changeFilter} />
                </header>

                {feed.status === 'error' && (
                    <div className="feed-state">
                        <p>Couldn't load the feed. Try again.</p>
                        <button type="button" className="feed-retry" onClick={feed.retry}>
                            Retry
                        </button>
                    </div>
                )}

                {feed.status === 'loading' && <p className="feed-state">Loading…</p>}

                {isEmpty && (
                    <p className="feed-state">
                        No topics in this view. Switch to All to find something to follow.
                    </p>
                )}

                {hasTopics && (
                    <>
                        <ul className="feed-list">
                            {feed.topics.map((topic) => (
                                <li key={topic.id}>
                                    <TopicCard topic={topic} onChange={feed.applySubscription} />
                                </li>
                            ))}
                        </ul>

                        <Pager
                            loaded={feed.topics.length}
                            total={total}
                            hasMore={feed.hasMore}
                            isLoading={feed.isLoadingMore}
                            pageSize={PAGE_SIZE}
                            errorMessage={feed.loadMoreError}
                            onLoadMore={feed.loadMore}
                        />
                    </>
                )}
            </div>

            <BackToTop />
        </div>
    );
}

export default Feed;
