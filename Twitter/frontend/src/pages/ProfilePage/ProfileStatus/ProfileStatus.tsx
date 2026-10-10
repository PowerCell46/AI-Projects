import PostListStatus from '../../../components/shared/PostListStatus/PostListStatus';
import type { BottomState } from '../../../components/shared/PostListStatus/PostListStatus';
import { useFocusOnMount } from '../../../hooks/useFocusOnMount';


const NOT_FOUND_TEXT = 'USER NOT FOUND';

interface ProfileStatusProps {
    state: BottomState;
    onRetry: () => void;
}

// What the page shows in place of a profile: the loading, empty (not found) and failed looks of the list bottom.
function ProfileStatus({ state, onRetry }: ProfileStatusProps) {
    const headingRef = useFocusOnMount<HTMLHeadingElement>();

    return (
        <>
            <h1 ref={headingRef} className="sr-only" tabIndex={-1}>Profile</h1>
            <PostListStatus
                state={state}
                endText={NOT_FOUND_TEXT}
                emptyText={NOT_FOUND_TEXT}
                onRetry={onRetry}
            />
        </>
    );
}

export default ProfileStatus;
