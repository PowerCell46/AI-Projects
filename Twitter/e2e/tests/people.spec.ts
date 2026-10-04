import { expect } from '@playwright/test';
import { confirmViaApi, follow, newUser, registerViaApi, test, type Account } from './fixtures';


interface PersonItem {
    id: string;
    username: string;
    followersCount: number;
    followedByMe: boolean;
}

interface PeoplePage {
    items: PersonItem[];
    nextCursor: string | null;
}

// Accounts register and confirm through the email link, so the first one also waits for the consumer's group join.
const TEST_TIMEOUT_MS = 120_000;

// The database is shared with the specs running in parallel, but the accounts of this test are the newest ones,
// so they are found on the first page or two.
const MAX_PAGES_TO_WALK = 5;

const LARGEST_PAGE_SIZE = 100;

test.describe.configure({ timeout: TEST_TIMEOUT_MS });

async function readPeoplePage(reader: Account, query: string): Promise<PeoplePage> {
    const response = await reader.api.get(`/api/v1/users${query}`);

    expect(response.ok()).toBe(true);

    return response.json();
}

// Follows nextCursor until every wanted username has been seen, and returns everyone seen on the way.
async function walkUntilFound(reader: Account, wantedUsernames: string[]): Promise<PersonItem[]> {
    const seenPeople: PersonItem[] = [];
    let cursor: string | null = null;

    for (let pageNumber = 0; pageNumber < MAX_PAGES_TO_WALK; pageNumber++) {
        const query: string = cursor === null ? '' : `?cursor=${cursor}`;
        const page = await readPeoplePage(reader, query);

        seenPeople.push(...page.items);
        cursor = page.nextCursor;

        const seenUsernames = seenPeople.map((person) => person.username);
        const hasFoundEveryone = wantedUsernames.every((username) => seenUsernames.includes(username));

        if (hasFoundEveryone || cursor === null) {
            break;
        }
    }

    return seenPeople;
}

function personNamed(people: PersonItem[], username: string): PersonItem {
    const person = people.find((candidate) => candidate.username === username);

    if (person === undefined) {
        throw new Error(`${username} is not in the people list.`);
    }

    return person;
}

test('should_list_the_other_accounts_and_flip_followed_by_me_when_the_caller_follows_one', async ({ createAccount }) => {
    const ana = await createAccount();
    const bob = await createAccount();
    const cy = await createAccount();
    const otherUsernames = [bob.user.username, cy.user.username];

    const peopleBefore = await walkUntilFound(ana, otherUsernames);

    expect(peopleBefore.map((person) => person.username)).not.toContain(ana.user.username);
    expect(personNamed(peopleBefore, bob.user.username).followedByMe).toBe(false);
    expect(personNamed(peopleBefore, cy.user.username).followedByMe).toBe(false);

    await follow(ana, bob);

    const peopleAfter = await walkUntilFound(ana, otherUsernames);

    expect(personNamed(peopleAfter, bob.user.username)).toMatchObject({
        followedByMe: true,
        followersCount: 1,
    });
    expect(personNamed(peopleAfter, cy.user.username)).toMatchObject({
        followedByMe: false,
        followersCount: 0,
    });
});

test('should_list_an_account_only_after_it_is_confirmed_and_answer_401_when_there_is_no_cookie', async ({ createAccount, request }) => {
    const reader = await createAccount();
    const pendingUser = newUser();

    await registerViaApi(request, pendingUser);

    const peopleWhilePending = await readPeoplePage(reader, `?size=${LARGEST_PAGE_SIZE}`);

    expect(peopleWhilePending.items.map((person) => person.username)).not.toContain(pendingUser.username);

    await confirmViaApi(request, pendingUser);

    const peopleAfterConfirmation = await readPeoplePage(reader, `?size=${LARGEST_PAGE_SIZE}`);

    expect(peopleAfterConfirmation.items.map((person) => person.username)).toContain(pendingUser.username);

    const response = await request.get(`/api/v1/users?size=${LARGEST_PAGE_SIZE}`);

    expect(response.status()).toBe(401);
});
