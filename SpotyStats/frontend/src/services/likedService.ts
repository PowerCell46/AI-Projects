import { apiGet } from './api'

export interface LikedTrack {
  trackId: string
  title: string
  artist: string
  album: string
  albumArtUrl: string | null
  addedAt: string
  durationMs: number
}

export interface LikedPage {
  total: number
  limit: number
  offset: number
  items: LikedTrack[]
}

export const LIKED_PAGE_SIZE = 10

/**
 * One page of the user's Spotify Liked Songs, most recently added first.
 */
export const fetchLikedPage = (offset: number): Promise<LikedPage> =>
  apiGet<LikedPage>(`/api/liked?limit=${LIKED_PAGE_SIZE}&offset=${offset}`)
