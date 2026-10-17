import './Pager.css';

interface PagerProps {
    loaded: number;
    total: number;
    hasMore: boolean;
    isLoading: boolean;
    pageSize: number;
    errorMessage: string | null;
    onLoadMore: () => void;
}

function Pager({ loaded, total, hasMore, isLoading, pageSize, errorMessage, onLoadMore }: PagerProps) {
    const nextCount = Math.min(pageSize, Math.max(0, total - loaded));

    return (
        <div className="pager">
            {hasMore ? (
                <button
                    type="button"
                    className="pager-control"
                    disabled={isLoading}
                    onClick={onLoadMore}
                >
                    <span className="pager-marker" aria-hidden="true">
                        <span className="pager-stem" />
                        <span className="pager-head" />
                    </span>

                    <span className="pager-text">
                        <span className="pager-action">
                            {isLoading ? 'Loading' : `Load the next ${nextCount}`}
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

            {errorMessage && <p className="pager-error">{errorMessage}</p>}
        </div>
    );
}

export default Pager;
