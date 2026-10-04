import { reportViews } from '../api/views';
import type { ReportViewsOptions } from '../api/views';


export const VIEW_BATCH_INTERVAL_MS = 5000;

export const MAX_VIEWS_PER_REQUEST = 50;

type SendViews = (tweetIds: string[], options: ReportViewsOptions) => Promise<void>;

// Collects the posts a reader has looked at and reports each one once. Reports are best-effort: a failed request is
// dropped, never retried, and its posts count as reported so they are not asked for again.
export class ViewReporter {
    private readonly reportedTweetIds = new Set<string>();
    private readonly sendViews: SendViews;
    private queuedTweetIds: string[] = [];
    private batchTimerId: number | null = null;

    constructor(sendViews: SendViews) {
        this.sendViews = sendViews;
    }

    hasReported(tweetId: string): boolean {
        return this.reportedTweetIds.has(tweetId);
    }

    record(tweetId: string): void {
        if (this.reportedTweetIds.has(tweetId)) {
            return;
        }

        this.reportedTweetIds.add(tweetId);
        this.queuedTweetIds.push(tweetId);

        if (this.batchTimerId === null) {
            this.batchTimerId = window.setTimeout(
                () => this.flush(),
                VIEW_BATCH_INTERVAL_MS,
            );
        }
    }

    flush(options: ReportViewsOptions = {}): void {
        if (this.batchTimerId !== null) {
            window.clearTimeout(this.batchTimerId);
            this.batchTimerId = null;
        }

        const tweetIds = this.queuedTweetIds;
        this.queuedTweetIds = [];

        for (let start = 0; start < tweetIds.length; start += MAX_VIEWS_PER_REQUEST) {
            const batch = tweetIds.slice(start, start + MAX_VIEWS_PER_REQUEST);

            this.sendViews(batch, options)
                .catch(() => undefined);
        }
    }
}

// One reporter for the whole page load, so a post seen on the feed is not reported again on the saved page.
export const viewReporter = new ViewReporter(reportViews);
