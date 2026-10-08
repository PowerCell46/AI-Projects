import { ApiError } from '../api/http';
import { SIGNAL_LOST_MESSAGE } from './authErrors';


export const MAX_TWEET_CHARACTERS = 280;

export const MAX_IMAGES = 4;

export const MAX_IMAGE_BYTES = 5_242_880;

export const TOO_MANY_IMAGES_MESSAGE = `A TWEET CAN HAVE AT MOST ${MAX_IMAGES} IMAGES`;

export const UNSUPPORTED_IMAGE_TYPE_MESSAGE = 'UNSUPPORTED IMAGE TYPE';

export const IMAGE_TOO_LARGE_MESSAGE = 'THE UPLOADED FILE IS TOO LARGE';

const FIRST_CLIENT_ERROR_STATUS = 400;

const FIRST_SERVER_ERROR_STATUS = 500;

export const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

export type PickedImage = Pick<File, 'size' | 'type'>;

// The tweet service counts Unicode code points of the trimmed text, so an emoji is one character here too.
export function countCharacters(text: string): number {
    return Array.from(text.trim()).length;
}

export function isOverCharacterLimit(text: string): boolean {
    return countCharacters(text) > MAX_TWEET_CHARACTERS;
}

export function canPublish(text: string, imageCount: number): boolean {
    const hasContent = countCharacters(text) > 0 || imageCount > 0;

    return hasContent && !isOverCharacterLimit(text);
}

// The browser's MIME type decides here; the server's magic-byte check still has the last word.
export function checkPickedImage(image: PickedImage, imageCount: number): string | null {
    if (imageCount >= MAX_IMAGES) {
        return TOO_MANY_IMAGES_MESSAGE;
    }

    if (!ALLOWED_IMAGE_TYPES.includes(image.type)) {
        return UNSUPPORTED_IMAGE_TYPE_MESSAGE;
    }

    return image.size > MAX_IMAGE_BYTES ? IMAGE_TOO_LARGE_MESSAGE : null;
}

function toUppercaseWithoutFinalPeriod(message: string): string {
    return message
        .replace(/\.$/, '')
        .toUpperCase();
}

// The server's own words when it refused the post (too long, wrong file type, ...); a lost connection or a server
// failure has nothing useful to say, so it reads as a lost signal.
export function describeUploadFailure(failure: unknown): string {
    const isRefusal = failure instanceof ApiError
        && failure.status >= FIRST_CLIENT_ERROR_STATUS
        && failure.status < FIRST_SERVER_ERROR_STATUS
        && failure.messages.length > 0;

    if (!isRefusal) {
        return SIGNAL_LOST_MESSAGE;
    }

    return failure.messages
        .map(toUppercaseWithoutFinalPeriod)
        .join('. ');
}
