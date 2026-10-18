import { fetchPeople } from '../../../api/accounts/users';
import ListFooter from '../../../components/shared/ListFooter/ListFooter';
import { useFollowUpdates } from '../../../contexts/FollowUpdatesContext';
import { useBottomSentinel } from '../../../hooks/lists/useBottomSentinel';
import { usePagedList } from '../../../hooks/lists/usePagedList';
import { bottomStateOf } from '../../../utils/posts/bottomState';
import PersonCard from '../../../components/shared/PersonCard/PersonCard';
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
    const { updates } = useFollowUpdates();
    const sentinelRef = useBottomSentinel(list.loadMore, `${list.loadedItems.length}-${list.status}`);
    const bottomState = bottomStateOf(list.status, list.isEnd, list.loadedItems.length);

    return (
        <>
            <ul className="people-list">
                {list.loadedItems.map((person) => {
                    // A follow reported from a profile re-keys the card, so its toggle restarts from the new values.
                    const shownPerson = { ...person, ...updates[person.username.toLowerCase()] };

                    return (
                        <PersonCard
                            key={`${person.id}-${shownPerson.followedByMe}-${shownPerson.followersCount}`}
                            person={shownPerson}
                            onFollowChanged={onFollowChanged}
                        />
                    );
                })}
            </ul>
            <ListFooter
                bottomState={bottomState}
                endText={END_TEXT}
                emptyText={EMPTY_TEXT}
                announcement={list.announcement}
                sentinelRef={sentinelRef}
                onRetry={list.retry}
            />
        </>
    );
}

export default PeopleList;
