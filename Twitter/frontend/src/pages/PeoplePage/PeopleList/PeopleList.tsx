import { fetchPeople } from '../../../api/users';
import PostListStatus from '../../../components/shared/PostListStatus/PostListStatus';
import { useBottomSentinel } from '../../../hooks/useBottomSentinel';
import { usePagedList } from '../../../hooks/usePagedList';
import { bottomStateOf } from '../../../utils/bottomState';
import PersonCard from './PersonCard/PersonCard';
import './PeopleList.css';


const END_TEXT = 'END OF PEOPLE';

const EMPTY_TEXT = 'NO ONE ELSE HERE YET';

function describeAppend(addedCount: number): string {
    return `${addedCount} more ${addedCount === 1 ? 'person' : 'people'} loaded`;
}

interface PeopleListProps {
    onFollowChanged: () => void;
}

function PeopleList({ onFollowChanged }: PeopleListProps) {
    const list = usePagedList(fetchPeople, describeAppend);
    const sentinelRef = useBottomSentinel(list.loadMore, `${list.loadedItems.length}-${list.status}`);
    const bottomState = bottomStateOf(list.status, list.isEnd, list.loadedItems.length);

    return (
        <>
            <ul className="person-list">
                {list.loadedItems.map((person) => (
                    <PersonCard key={person.id} person={person} onFollowChanged={onFollowChanged} />
                ))}
            </ul>
            {bottomState && (
                <PostListStatus state={bottomState} endText={END_TEXT} emptyText={EMPTY_TEXT} onRetry={list.retry} />
            )}
            <div ref={sentinelRef} />
            <p className="sr-only" aria-live="polite">{list.announcement}</p>
        </>
    );
}

export default PeopleList;
