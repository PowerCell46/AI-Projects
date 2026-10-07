import { useMemo, useState } from 'react';
import { fetchReplies } from '../../api/replies';
import type { Reply } from '../../api/replies';
import { usePagedList } from '../../hooks/usePagedList';
import type { LoadStatus } from '../../hooks/usePagedList';
import { appendUnique } from '../../utils/tweetList';


export interface ReplyThreadState {
    replies: Reply[];
    status: LoadStatus;
    isEnd: boolean;
    announcement: string;
    countChange: number;
    loadMore: () => void;
    retry: () => void;
    addSentReply: (reply: Reply) => void;
    replaceReply: (reply: Reply) => void;
    removeReply: (replyId: string) => void;
}

function describeAppend(addedCount: number): string {
    return `${addedCount} more ${addedCount === 1 ? 'reply' : 'replies'} loaded`;
}

// The replies of one post, oldest first, paged like the post lists. A reply you send is shown straight under the
// composer, newest of yours first, until the server delivers the same id and the loaded copy takes its real place.
// An edited reply replaces its older copy and a deleted one is hidden, wherever it came from; `countChange` is what
// your sends and deletes did to the post's reply count.
export function useReplyThread(tweetId: string): ReplyThreadState {
    const pagedList = usePagedList(
        (request) => fetchReplies(tweetId, request),
        describeAppend,
    );
    const [sentReplies, setSentReplies] = useState<Reply[]>([]);
    const [editedReplies, setEditedReplies] = useState<Reply[]>([]);
    const [removedReplyIds, setRemovedReplyIds] = useState<string[]>([]);

    const replies = useMemo(
        () => appendUnique(sentReplies, pagedList.loadedItems)
            .filter((reply) => !removedReplyIds.includes(reply.id))
            .map((reply) => editedReplies.find((edited) => edited.id === reply.id) ?? reply),
        [sentReplies, pagedList.loadedItems, editedReplies, removedReplyIds],
    );

    function addSentReply(reply: Reply) {
        setSentReplies((currentReplies) => [reply, ...currentReplies]);
    }

    function replaceReply(reply: Reply) {
        setEditedReplies((currentReplies) => [reply, ...currentReplies.filter((edited) => edited.id !== reply.id)]);
    }

    function removeReply(replyId: string) {
        setRemovedReplyIds((currentIds) => [...currentIds, replyId]);
    }

    return {
        replies,
        status: pagedList.status,
        isEnd: pagedList.isEnd,
        announcement: pagedList.announcement,
        countChange: sentReplies.length - removedReplyIds.length,
        loadMore: pagedList.loadMore,
        retry: pagedList.retry,
        addSentReply,
        replaceReply,
        removeReply,
    };
}
