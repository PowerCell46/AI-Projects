import { Link } from 'react-router-dom';
import type { Person } from '../../../api/accounts/users';
import PersonCard from '../../../components/shared/PersonCard/PersonCard';
import { ROUTES } from '../../../routes';
import { useSuggestedPeople } from './useSuggestedPeople';
import './FeedEmptyPanel.css';


const HEADING = 'THE SURFACE IS QUIET';

const EMPTY_TEXT = 'NOTHING HERE YET — FOLLOW SOMEONE TO SEE THEIR POSTS';

const PEOPLE_TAB_LABEL = 'PEOPLE TAB';

const HINT_WITH_SUGGESTIONS = 'THESE ARE YOUR FIRST SUGGESTIONS. FOR MORE, OPEN THE ';

const HINT_WITHOUT_SUGGESTIONS = 'FIND PEOPLE IN THE ';

const PREVIEW_CAPTION = 'YOUR FEED WILL LOOK LIKE THIS';

const GHOST_POST_COUNT = 3;

const GHOST_POST_IDS = Array.from({ length: GHOST_POST_COUNT }, (_, ghostIndex) => `ghost-post-${ghostIndex}`);

interface FeedEmptyPanelProps {
    onFollowChanged: (person: Person, isFollowing: boolean) => void;
}

function FeedEmptyPanel({ onFollowChanged }: FeedEmptyPanelProps) {
    const suggestions = useSuggestedPeople();
    const hasSuggestions = suggestions.length > 0;

    return (
        <section className="feed-empty-panel" aria-labelledby="feed-empty-panel-heading">
            <h2 className="feed-empty-panel-heading" id="feed-empty-panel-heading">{HEADING}</h2>
            <p className="feed-empty-panel-text">{EMPTY_TEXT}</p>
            {hasSuggestions && (
                <ul className="feed-empty-panel-people">
                    {suggestions.map((person) => (
                        <PersonCard
                            key={person.id}
                            person={person}
                            onFollowChanged={(isFollowing) => onFollowChanged(person, isFollowing)}
                        />
                    ))}
                </ul>
            )}
            <p className="feed-empty-panel-hint">
                {hasSuggestions ? HINT_WITH_SUGGESTIONS : HINT_WITHOUT_SUGGESTIONS}
                <Link className="feed-empty-panel-link" to={ROUTES.people}>{PEOPLE_TAB_LABEL}</Link>
                .
            </p>
            <p className="feed-empty-panel-caption" aria-hidden="true">{PREVIEW_CAPTION}</p>
            <ul className="feed-empty-panel-ghosts" aria-hidden="true">
                {GHOST_POST_IDS.map((ghostId) => (
                    <li key={ghostId} className="feed-empty-panel-ghost">
                        <span className="feed-empty-panel-ghost-avatar" />
                        <span className="feed-empty-panel-ghost-name" />
                        <span className="feed-empty-panel-ghost-line" />
                        <span className="feed-empty-panel-ghost-line" data-short="true" />
                    </li>
                ))}
            </ul>
        </section>
    );
}

export default FeedEmptyPanel;
