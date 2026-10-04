import { stripBidiControls } from './bidi';


const BIO_MAX_CODE_POINTS = 50;

const ELLIPSIS = '…';

const WHITESPACE_RUN_PATTERN = /\s+/g;

// A card keeps every bio to one short line so cards stay the same height: no word is left half, except a first word
// that is itself longer than the limit. Null means there is nothing to show.
export function truncateBio(bio: string | null): string | null {
    if (bio === null) {
        return null;
    }

    const oneLine = stripBidiControls(bio)
        .replace(WHITESPACE_RUN_PATTERN, ' ')
        .trim();

    if (oneLine === '') {
        return null;
    }

    const codePoints = Array.from(oneLine);

    if (codePoints.length <= BIO_MAX_CODE_POINTS) {
        return oneLine;
    }

    const head = codePoints
        .slice(0, BIO_MAX_CODE_POINTS)
        .join('');
    const lastSpaceIndex = head.lastIndexOf(' ');
    const cutText = lastSpaceIndex === -1 ? head : head.slice(0, lastSpaceIndex);

    return `${cutText}${ELLIPSIS}`;
}
