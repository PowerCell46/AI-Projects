// The index a step away from the current one, wrapping round at both ends.
export function wrapIndex(currentIndex: number, step: number, itemCount: number): number {
    return (currentIndex + step + itemCount) % itemCount;
}
