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
    const progress = total > 0 ? Math.min(100, (loaded / total) * 100) : 0

    return (
        <div className="pager">
            <div className="pager-row">
                <span className="pager-readout">
                    {loaded} of {total}
                </span>

                <div className="pager-track">
                    <div className="pager-fill" style={{ width: `${progress}%` }} />
                </div>

                {hasMore && (
                    <button type="button" className="pager-button" onClick={onLoadMore} disabled={loading}>
                        {loading ? 'Loading' : `Next ${pageSize}`}
                    </button>
                )}
            </div>

            {error && <p className="pager-error">{error}</p>}
        </div>
    )
}

export default Pager
