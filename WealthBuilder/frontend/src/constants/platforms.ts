// Outbound links to the brokers and exchanges users actually trade on. WealthBuilder only
// tracks holdings — it never executes trades — so these are pure convenience shortcuts.
// Add or remove entries here; the dashboard panel renders whatever this list contains.


export interface Platform {
    name: string;
    category: string;
    url: string;
}


export const INVESTMENT_PLATFORMS: Platform[] = [
    { name: 'Binance', category: 'Crypto exchange', url: 'https://www.binance.com' },
    { name: 'Trading 212', category: 'Stocks & ETFs', url: 'https://www.trading212.com' },
    { name: 'TopGold.bg', category: 'Precious metals', url: 'https://www.topgold.bg' },
    { name: 'Admirals', category: 'Multi-asset', url: 'https://www.admiralmarkets.com' },
    { name: 'Revolut', category: 'Stocks & crypto', url: 'https://www.revolut.com' },
    { name: 'Kraken', category: 'Crypto exchange', url: 'https://www.kraken.com' },
];
