import { createContext } from 'react';
import type { AuthContextValue } from '../../types/auth';


// Kept in its own file (no component export) so react-refresh stays happy.
export const AuthContext = createContext<AuthContextValue | null>(null);
