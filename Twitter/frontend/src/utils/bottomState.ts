import type { BottomState } from '../components/shared/PostListStatus/PostListStatus';
import type { LoadStatus } from '../hooks/usePagedList';


// What the foot of a paged list says: a fetch in flight or failed, the end, or an empty list; nothing while more
// pages are waiting.
export function bottomStateOf(status: LoadStatus, isEnd: boolean, itemCount: number): BottomState | null {
    if (status === 'loading' || status === 'failed') {
        return status;
    }

    if (isEnd) {
        return itemCount === 0 ? 'empty' : 'end';
    }

    return null;
}
