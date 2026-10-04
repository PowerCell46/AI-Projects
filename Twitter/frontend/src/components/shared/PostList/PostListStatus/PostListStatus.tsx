import './PostListStatus.css';


export type BottomState = 'loading' | 'end' | 'empty' | 'failed';

interface PostListStatusProps {
    state: BottomState;
    endText: string;
    emptyText: string;
    onRetry: () => void;
}

function PostListStatus({ state, endText, emptyText, onRetry }: PostListStatusProps) {
    if (state === 'loading') {
        return (
            <p className="post-list-status">
                <span className="post-list-status-square" aria-hidden="true" />
                FETCHING MORE
            </p>
        );
    }

    if (state === 'failed') {
        return (
            <div className="post-list-status" role="alert">
                <span>SIGNAL LOST</span>
                <button type="button" className="post-list-status-retry" onClick={onRetry}>TRY AGAIN</button>
            </div>
        );
    }

    return <p className="post-list-status">{state === 'end' ? endText : emptyText}</p>;
}

export default PostListStatus;
