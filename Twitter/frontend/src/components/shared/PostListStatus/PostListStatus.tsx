import './PostListStatus.css';


export type BottomState = 'loading' | 'end' | 'empty' | 'failed';

export interface EmptyAction {
    label: string;
    onClick: () => void;
}

interface PostListStatusProps {
    state: BottomState;
    endText: string;
    emptyText: string;
    onRetry: () => void;
    emptyAction?: EmptyAction;
}

function PostListStatus({ state, endText, emptyText, onRetry, emptyAction }: PostListStatusProps) {
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
                <button type="button" className="post-list-status-button" onClick={onRetry}>TRY AGAIN</button>
            </div>
        );
    }

    if (state === 'empty' && emptyAction) {
        return (
            <div className="post-list-status">
                <span>{emptyText}</span>
                <button type="button" className="post-list-status-button" onClick={emptyAction.onClick}>
                    {emptyAction.label}
                </button>
            </div>
        );
    }

    return <p className="post-list-status">{state === 'end' ? endText : emptyText}</p>;
}

export default PostListStatus;
