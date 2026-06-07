import { apiGet } from './api'


export interface ListeningTotals {
  totalPlays: number
  totalListeningTimeMs: number
  uniqueArtists: number
  uniqueTracks: number
  trackingSince: string | null
  likedTotal: number | null
}

export interface Profile {
  spotifyUserId: string
  displayName: string | null
  email: string | null
  imageUrl: string | null
  country: string | null
  product: string | null
  followers: number | null
  totals: ListeningTotals
}

const browserZone = (): string => Intl.DateTimeFormat().resolvedOptions().timeZone

export const fetchProfile = (): Promise<Profile> =>
  apiGet<Profile>(`/api/profile?zone=${encodeURIComponent(browserZone())}`)
