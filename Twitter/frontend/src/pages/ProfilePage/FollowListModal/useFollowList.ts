import { fetchFollowers, fetchFollowing } from '../../../api/accounts/users';
import type { PageRequest } from '../../../api/core/paging';
import { usePagedList } from '../../../hooks/lists/usePagedList';
import type { PagedListState } from '../../../hooks/lists/usePagedList';
import type { Person } from '../../../api/accounts/users';
import type { FollowListKind } from './followListKind';


function describeAppend(addedCount: number): string {
    return `${addedCount} more ${addedCount === 1 ? 'person' : 'people'} loaded`;
}

// One list for both kinds; only the endpoint differs. It reads the first page on mount, so each opening starts fresh.
export function useFollowList(kind: FollowListKind, username: string): PagedListState<Person> {
    function fetchPage(request: PageRequest) {
        return kind === 'followers' ? fetchFollowers(username, request) : fetchFollowing(username, request);
    }

    return usePagedList(fetchPage, describeAppend);
}
