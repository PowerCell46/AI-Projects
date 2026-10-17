export const FRONTEND_URL = 'http://localhost:8090';

export const MAILPIT_URL = 'http://localhost:8125';

// Satisfies the gateway's password rules (8-72 chars, upper + lower + digit).
export const TEST_PASSWORD = 'Password123';

// Must match the page size the frontend requests, or the paging tests stop crossing a page boundary.
export const FEED_PAGE_SIZE = 20;

// Upper bound on "load more" clicks, so a pager that never runs out fails the test instead of hanging it.
export const MAX_PAGER_LOADS = 100;
