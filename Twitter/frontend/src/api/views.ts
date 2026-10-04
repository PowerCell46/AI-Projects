import { ENDPOINTS } from './endpoints';
import { jsonRequest, sendAuthenticated } from './http';


export interface ReportViewsOptions {
    isKeepalive?: boolean;
}

export async function reportViews(tweetIds: string[], options: ReportViewsOptions = {}): Promise<void> {
    await sendAuthenticated(ENDPOINTS.views, {
        ...jsonRequest(
            'POST',
            { tweetIds },
        ),
        keepalive: options.isKeepalive ?? false,
    });
}
