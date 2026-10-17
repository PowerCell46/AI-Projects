interface AscentLocationState {
    isAscending: true;
}

export const ASCENT_LOCATION_STATE: AscentLocationState = { isAscending: true };

export function isAscentLocationState(locationState: unknown): boolean {
    return typeof locationState === 'object'
        && locationState !== null
        && 'isAscending' in locationState
        && locationState.isAscending === true;
}
