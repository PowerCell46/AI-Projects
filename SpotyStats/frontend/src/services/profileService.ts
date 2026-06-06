import { apiGet } from './api'


export interface Profile {
  spotifyUserId: string
  displayName: string | null
  email: string | null
  imageUrl: string | null
}

export const fetchProfile = (): Promise<Profile> =>
  apiGet<Profile>('/api/profile')
