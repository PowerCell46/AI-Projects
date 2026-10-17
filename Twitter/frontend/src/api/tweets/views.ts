import { ENDPOINTS } from '../core/endpoints';
import { jsonRequest, sendAuthenticated } from '../core/http';


export interface ReportViewsOptions {
    isKeepalive?: boolean;
}

export async function reportViews(tweetIds: string[], options: ReportViewsOptions = {}): Promise<void> {
    await sendAuthenticated(
        ENDPOINTS.views,
        {
            ...jsonRequest(
                'POST',
                { tweetIds },
            ),
            keepalive: options.isKeepalive ?? false,
        },
    );
}
