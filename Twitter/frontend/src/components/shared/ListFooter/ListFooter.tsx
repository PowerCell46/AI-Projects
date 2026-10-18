import type { RefObject } from 'react';
import PostListStatus from '../PostListStatus/PostListStatus';
import type { BottomState } from '../PostListStatus/PostListStatus';


interface ListFooterProps {
    bottomState: BottomState | null;
    endText: string;
    emptyText: string;
    announcement: string;
    sentinelRef: RefObject<HTMLDivElement | null>;
    onRetry: () => void;
}

// The foot of every paged list: what its bottom says, the sentinel that loads more above it, and the polite
// announcement of what arrived.
function ListFooter({
    bottomState,
    endText,
    emptyText,
    announcement,
    sentinelRef,
    onRetry,
}: ListFooterProps) {
    return (
        <>
            {bottomState && (
                <PostListStatus
                    state={bottomState}
                    endText={endText}
                    emptyText={emptyText}
                    onRetry={onRetry}
                />
            )}
            <div ref={sentinelRef} />
            <p className="sr-only" aria-live="polite">{announcement}</p>
        </>
    );
}

export default ListFooter;
