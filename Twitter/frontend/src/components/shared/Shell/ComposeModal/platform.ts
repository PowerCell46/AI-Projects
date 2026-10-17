// Decides which shortcut hint to print; the shortcut itself accepts both modifiers everywhere.
export function isApplePlatform(): boolean {
    return /Mac|iPhone|iPad/.test(navigator.userAgent);
}
