// True when the click landed on, or inside, an element that matches the selector.
export function isInsideElement(target: EventTarget, selector: string): boolean {
    return target instanceof Element && target.closest(selector) !== null;
}

// A click that ends a drag over text has a selection: it was not meant to open anything.
export function hasTextSelection(): boolean {
    return (window.getSelection()?.toString() ?? '') !== '';
}
