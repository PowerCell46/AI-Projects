// Turns an asset name into a URL slug: lowercased, with every run of non-alphanumeric
// characters collapsed to a single dash and leading/trailing dashes trimmed. "Precious Metals"
// becomes "precious-metals". Slugifying both the asset name and the route segment makes the
// match inherently case- and spacing-insensitive.

export const slugify = (value: string): string => {
    return value
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, '-')
        .replace(/^-+|-+$/g, '');
};
