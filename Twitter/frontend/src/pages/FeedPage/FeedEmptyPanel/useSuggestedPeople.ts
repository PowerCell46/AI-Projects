import { useEffect, useState } from 'react';
import { fetchPeople } from '../../../api/accounts/users';
import type { Person } from '../../../api/accounts/users';


const SUGGESTION_COUNT = 3;

// Some of the first page may already be followed, so more than the shown count is read.
const SUGGESTION_POOL_SIZE = 10;

// The first few people the reader does not follow yet. A failed read shows no suggestions: the panel still points to
// the People tab. People followed afterwards stay in the list, because the list is read once.
export function useSuggestedPeople(): Person[] {
    const [suggestions, setSuggestions] = useState<Person[]>([]);

    useEffect(() => {
        let isCancelled = false;

        fetchPeople({
            cursor: null,
            size: SUGGESTION_POOL_SIZE,
        })
            .then((page) => {
                if (isCancelled) {
                    return;
                }

                setSuggestions(page.items
                    .filter((person) => !person.followedByMe)
                    .slice(0, SUGGESTION_COUNT));
            })
            .catch(() => undefined);

        return () => {
            isCancelled = true;
        };
    }, []);

    return suggestions;
}
