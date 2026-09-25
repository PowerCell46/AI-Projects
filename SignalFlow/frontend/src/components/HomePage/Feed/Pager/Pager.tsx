import './Pager.css'

interface PagerProps {
    loaded: number
    total: number
    hasMore: boolean
    loading: boolean
    pageSize: number
    error: string | null
    onLoadMore: () => void
}

function Pager({ loaded, total, hasMore, loading, pageSize, error, onLoadMore }: PagerProps) {
    const nextCount = Math.min(pageSize, Math.max(0, total - loaded))

    return (
        <div className="pager">
            {hasMore ? (
                <button type="button" className="pager-control" onClick={onLoadMore} disabled={loading}>
                    <span className="pager-marker" aria-hidden="true">
                        <span className="pager-stem" />
                        <span className="pager-head" />
                    </span>

                    <span className="pager-text">
                        <span className="pager-action">
                            {loading ? 'Loading' : `Load the next ${nextCount}`}
                        </span>

                        <span className="pager-count">
                            {loaded} of {total} loaded
                        </span>
                    </span>
                </button>
            ) : (
                <p className="pager-count">
                    {loaded} of {total} loaded
                </p>
            )}

            {error && <p className="pager-error">{error}</p>}
        </div>
    )
}

export default Pager
