import type { Ref } from 'react';
import GlowButton from '../../../components/shared/GlowButton/GlowButton';


interface ProfileEditButtonProps {
    ref: Ref<HTMLButtonElement>;
    onClick: () => void;
}

function ProfileEditButton({ ref, onClick }: ProfileEditButtonProps) {
    return <GlowButton label="EDIT" ref={ref} onClick={onClick} />;
}

export default ProfileEditButton;
