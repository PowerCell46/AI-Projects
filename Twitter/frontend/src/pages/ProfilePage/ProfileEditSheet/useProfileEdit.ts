import { useRef, useState } from 'react';
import { updateProfile, uploadProfilePicture } from '../../../api/users';
import type { UserProfile } from '../../../api/users';
import { describeUploadFailure } from '../../../utils/composeChecks';
import {
    changedFields,
    describeSaveFailure,
    isBioOverLimit,
    isEmptyChange,
    isLocationOverLimit,
} from '../../../utils/profileEdit';
import type { SaveErrors } from '../../../utils/profileEdit';


const NO_ERRORS: SaveErrors = {
    bio: '',
    location: '',
    photo: '',
    general: '',
};

export interface ProfileEdit {
    bio: string;
    location: string;
    errors: SaveErrors;
    isSaving: boolean;
    isDirty: boolean;
    isBioOver: boolean;
    isLocationOver: boolean;
    changeBio: (bio: string) => void;
    changeLocation: (location: string) => void;
    clearPhotoError: () => void;
    save: () => void;
}

interface ProfileEditCallbacks {
    onSaved: (savedProfile: UserProfile) => void;
    onFinished: () => void;
}

// The draft of the edit sheet. Saving sends only what changed: the text first, then the picked photo. Every answer of
// the server goes to `onSaved` as it comes, so text that went through counts as saved even when the photo then fails;
// the retry sends the photo alone. A refusal keeps the draft and names the field, one save runs at a time, and with
// nothing changed nothing is sent and the sheet just finishes.
export function useProfileEdit(
    profile: UserProfile,
    photo: File | null,
    { onSaved, onFinished }: ProfileEditCallbacks,
): ProfileEdit {
    const [bio, setBio] = useState(profile.bio ?? '');
    const [location, setLocation] = useState(profile.location ?? '');
    const [errors, setErrors] = useState(NO_ERRORS);
    const [isSaving, setIsSaving] = useState(false);
    const isSavingRef = useRef(false);
    const textChange = changedFields(profile, {
        bio,
        location,
    });
    const isBioOver = isBioOverLimit(bio);
    const isLocationOver = isLocationOverLimit(location);

    function changeBio(nextBio: string) {
        setBio(nextBio);
        setErrors({
            ...errors,
            bio: '',
            general: '',
        });
    }

    function changeLocation(nextLocation: string) {
        setLocation(nextLocation);
        setErrors({
            ...errors,
            location: '',
            general: '',
        });
    }

    function clearPhotoError() {
        setErrors({
            ...errors,
            photo: '',
        });
    }

    async function saveText(): Promise<boolean> {
        if (isEmptyChange(textChange)) {
            return true;
        }

        try {
            onSaved(await updateProfile(textChange));

            return true;

        } catch (failure) {
            setErrors(describeSaveFailure(failure));

            return false;
        }
    }

    async function savePhoto(): Promise<boolean> {
        if (photo === null) {
            return true;
        }

        try {
            onSaved(await uploadProfilePicture(photo));

            return true;

        } catch (failure) {
            setErrors({
                ...NO_ERRORS,
                photo: describeUploadFailure(failure),
            });

            return false;
        }
    }

    async function save() {
        if (isSavingRef.current || isBioOver || isLocationOver) {
            return;
        }

        isSavingRef.current = true;
        setIsSaving(true);
        setErrors(NO_ERRORS);

        const hasSaved = await saveText() && await savePhoto();

        if (hasSaved) {
            onFinished();

        } else {
            setIsSaving(false);
            isSavingRef.current = false;
        }
    }

    return {
        bio,
        location,
        errors,
        isSaving,
        isDirty: !isEmptyChange(textChange) || photo !== null,
        isBioOver,
        isLocationOver,
        changeBio,
        changeLocation,
        clearPhotoError,
        save,
    };
}
