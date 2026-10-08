import { ApiError } from '../api/http';
import type { ProfileChange } from '../api/users';
import { SIGNAL_LOST_MESSAGE } from './authErrors';


export const PROFILE_BIO_MAX_CODE_POINTS = 160;

export const PROFILE_LOCATION_MAX_CODE_POINTS = 60;

export const PROFILE_PHOTO_HINT = 'JPG, PNG OR WEBP · MAX 5 MB';

const STATUS_BAD_REQUEST = 400;

const BIO_MESSAGE_PREFIX = 'bio ';

const LOCATION_MESSAGE_PREFIX = 'location ';

export interface ProfileText {
    bio: string | null;
    location: string | null;
}

export interface ProfileDraft {
    bio: string;
    location: string;
}

export interface SaveErrors {
    bio: string;
    location: string;
    photo: string;
    general: string;
}

// The server counts Unicode code points of the stripped text, so an emoji is one character here too.
export function countProfileCharacters(text: string): number {
    return Array.from(text.trim()).length;
}

export function isBioOverLimit(bio: string): boolean {
    return countProfileCharacters(bio) > PROFILE_BIO_MAX_CODE_POINTS;
}

export function isLocationOverLimit(location: string): boolean {
    return countProfileCharacters(location) > PROFILE_LOCATION_MAX_CODE_POINTS;
}

// Only what differs from the saved text, trimmed; an empty string clears a field. Nothing differing means nothing to
// send.
export function changedFields(saved: ProfileText, draft: ProfileDraft): ProfileChange {
    const change: ProfileChange = {};
    const bio = draft.bio.trim();
    const location = draft.location.trim();

    if (bio !== (saved.bio ?? '').trim()) {
        change.bio = bio;
    }

    if (location !== (saved.location ?? '').trim()) {
        change.location = location;
    }

    return change;
}

export function isEmptyChange(change: ProfileChange): boolean {
    return change.bio === undefined && change.location === undefined;
}

function toUppercaseWithoutFinalPeriod(message: string): string {
    return message
        .replace(/\.$/, '')
        .toUpperCase();
}

// A refusal names its field ("bio must be at most 160 characters") and goes under that field; any other refusal goes
// beside the buttons. A lost connection or a server failure has nothing useful to say, so it reads as a lost signal.
export function describeSaveFailure(failure: unknown): SaveErrors {
    const errors: SaveErrors = {
        bio: '',
        location: '',
        photo: '',
        general: '',
    };

    if (!(failure instanceof ApiError) || failure.status !== STATUS_BAD_REQUEST || failure.messages.length === 0) {
        errors.general = SIGNAL_LOST_MESSAGE;

        return errors;
    }

    const otherMessages: string[] = [];

    failure.messages.forEach((message) => {
        if (message.startsWith(BIO_MESSAGE_PREFIX)) {
            errors.bio = toUppercaseWithoutFinalPeriod(message);

        } else if (message.startsWith(LOCATION_MESSAGE_PREFIX)) {
            errors.location = toUppercaseWithoutFinalPeriod(message);

        } else {
            otherMessages.push(toUppercaseWithoutFinalPeriod(message));
        }
    });

    errors.general = otherMessages.join('. ');

    return errors;
}
