import PostListStatus from '../../../components/shared/PostListStatus/PostListStatus';
import type { BottomState } from '../../../components/shared/PostListStatus/PostListStatus';


const NOT_FOUND_TEXT = 'USER NOT FOUND';

interface ProfileStatusProps {
    state: BottomState;
    onRetry: () => void;
}

// What the page shows in place of a profile: the loading, empty (not found) and failed looks of the list bottom.
function ProfileStatus({ state, onRetry }: ProfileStatusProps) {
    return (
        <>
            <h1 className="sr-only">Profile</h1>
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
