import { useMemo } from 'react';
import type { Page, PageRequest } from '../../../api/paging';
import type { TweetItem } from '../../../api/tweetPage';
import { usePagedList } from '../../../hooks/usePagedList';
import type { PagedListState } from '../../../hooks/usePagedList';
import { mergeNewestFirst } from '../../../utils/tweetList';


export interface PostListState extends PagedListState<TweetItem> {
    items: TweetItem[];
}

function describeAppend(addedCount: number): string {
    return `${addedCount} more ${addedCount === 1 ? 'post' : 'posts'} loaded`;
}

// One list for both pages; only the page fetcher differs. `ownPosts` are the reader's own new posts: they are shown in
// their place by time until the server delivers the same id, and then the loaded copy wins.
export function usePostList(
    fetchPage: (request: PageRequest) => Promise<Page<TweetItem>>,
    ownPosts: TweetItem[],
): PostListState {
    const pagedList = usePagedList(fetchPage, describeAppend);

    const items = useMemo(
        () => mergeNewestFirst(pagedList.loadedItems, ownPosts),
        [pagedList.loadedItems, ownPosts],
    );

    return {
        ...pagedList,
        items,
    };
}
