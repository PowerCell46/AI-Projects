const BASE_URL = import.meta.env.VITE_BASE_API_URL ?? ''

export interface ShortUrlResponse {
    shortUrl: string
}

export async function createShortUrl(url: string): Promise<ShortUrlResponse> {
    const response = await fetch(`${BASE_URL}/api/v1/urls`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({ url }),
    })

    if (!response.ok) {
        throw new Error(`Request failed with status ${response.status}`)
    }

    return response.json() as Promise<ShortUrlResponse>
}
