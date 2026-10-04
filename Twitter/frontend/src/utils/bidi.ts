// The embedding, override and isolate controls (U+202A-202E, U+2066-2069) can make text reorder the page around
// it. LRM/RLM, emoji joiners and right-to-left scripts are not matched, so they stay.
const BIDI_CONTROL_PATTERN = /[‪-‮⁦-⁩]/g;

export function stripBidiControls(text: string): string {
    return text.replace(BIDI_CONTROL_PATTERN, '');
}
