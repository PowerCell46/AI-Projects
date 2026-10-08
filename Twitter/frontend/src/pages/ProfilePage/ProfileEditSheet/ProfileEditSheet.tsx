import { useEffect, useId, useRef } from 'react';
import type { ChangeEvent, FormEvent, KeyboardEvent } from 'react';
import type { UserProfile } from '../../../api/users';
import Avatar from '../../../components/shared/Avatar/Avatar';
import { useArmPhase } from '../../../hooks/useArmPhase';
import { useFocusTrap } from '../../../hooks/useFocusTrap';
import { useOverLimitAnnouncement } from '../../../hooks/useOverLimitAnnouncement';
import { useScrollLock } from '../../../hooks/useScrollLock';
import { ALLOWED_IMAGE_TYPES } from '../../../utils/composeChecks';
import {
    countProfileCharacters,
    PROFILE_BIO_MAX_CODE_POINTS,
    PROFILE_LOCATION_MAX_CODE_POINTS,
    PROFILE_PHOTO_HINT,
} from '../../../utils/profileEdit';
import LockedField from './LockedField/LockedField';
import { usePhotoPick } from './usePhotoPick';
import { useProfileEdit } from './useProfileEdit';
import './ProfileEditSheet.css';


// Must stay shorter than the 340ms fade-in in ProfileEditSheet.css, so the bio is focused while the sheet is still
// appearing.
const AUTOFOCUS_DELAY_MS = 80;

const ACCEPTED_IMAGE_TYPES = ALLOWED_IMAGE_TYPES.join(',');

const OVER_LIMIT_MESSAGE = `Bio is over the ${PROFILE_BIO_MAX_CODE_POINTS} character limit.`;

const WITHIN_LIMIT_MESSAGE = 'Bio is within the limit.';

const LOCATION_OVER_MESSAGE = `LOCATION MUST BE AT MOST ${PROFILE_LOCATION_MAX_CODE_POINTS} CHARACTERS`;

interface ProfileEditSheetProps {
    profile: UserProfile;
    email: string;
    onSaved: (savedProfile: UserProfile) => void;
    onClose: () => void;
}

function ProfileEditSheet({ profile, email, onSaved, onClose }: ProfileEditSheetProps) {
    const photo = usePhotoPick();
    const edit = useProfileEdit(
        profile,
        photo.file,
        {
            onSaved,
            onFinished: onClose,
        },
    );
    const discard = useArmPhase();
    const overLimitAnnouncement = useOverLimitAnnouncement(edit.isBioOver, OVER_LIMIT_MESSAGE, WITHIN_LIMIT_MESSAGE);

    useScrollLock();

    const headlineId = useId();
    const bioId = useId();
    const bioErrorId = useId();
    const counterId = useId();
    const locationId = useId();
    const locationErrorId = useId();
    const dialogRef = useRef<HTMLDivElement>(null);
    const bioRef = useRef<HTMLTextAreaElement>(null);
    const handleTrapKeyDown = useFocusTrap(dialogRef);
    const isArmed = discard.phase === 'armed';
    const locationError = edit.errors.location || (edit.isLocationOver ? LOCATION_OVER_MESSAGE : '');
    const photoError = edit.errors.photo || photo.errorMessage;
    const isSaveBlocked = edit.isSaving || edit.isBioOver || edit.isLocationOver;

    // The sheet fades in first, so the bio takes focus a moment after it opens.
    useEffect(() => {
        const timerId = window.setTimeout(
            () => bioRef.current?.focus(),
            AUTOFOCUS_DELAY_MS,
        );

        return () => window.clearTimeout(timerId);
    }, []);

    // The first press asks "discard changes?" for three seconds; a second press or Escape within them discards.
    function handleCancel() {
        if (edit.isSaving) {
            return;
        }

        if (!edit.isDirty || isArmed) {
            onClose();

        } else {
            discard.arm();
        }
    }

    function handlePhotoPick(event: ChangeEvent<HTMLInputElement>) {
        discard.reset();
        edit.clearPhotoError();
        photo.pick(Array.from(event.currentTarget.files ?? []));

        // Cleared so picking the same file again after a refusal still fires a change.
        event.currentTarget.value = '';
    }

    function handleBioChange(bio: string) {
        discard.reset();
        edit.changeBio(bio);
    }

    function handleLocationChange(location: string) {
        discard.reset();
        edit.changeLocation(location);
    }

    function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        discard.reset();
        edit.save();
    }

    function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
        handleTrapKeyDown(event);

        if (event.key === 'Escape') {
            event.preventDefault();
            handleCancel();
        }
    }

    return (
        <div
            className="profile-edit-sheet"
            aria-modal="true"
            aria-labelledby={headlineId}
            role="dialog"
            ref={dialogRef}
            onKeyDown={handleKeyDown}
        >
            <form className="profile-edit-sheet-content" noValidate onSubmit={handleSubmit}>
                <p className="profile-edit-sheet-eyebrow">EDIT PROFILE</p>
                <h2 id={headlineId} className="profile-edit-sheet-headline">Say who you are.</h2>
                <div className="profile-edit-sheet-photo">
                    <Avatar pictureUrl={photo.previewUrl ?? profile.profilePictureUrl} size="huge" />
                    <div className="profile-edit-sheet-photo-text">
                        <label className="profile-edit-sheet-photo-pick">
                            <input
                                type="file"
                                className="sr-only"
                                accept={ACCEPTED_IMAGE_TYPES}
                                onChange={handlePhotoPick}
                            />
                            CHANGE PHOTO
                        </label>
                        <p className="profile-edit-sheet-hint">{PROFILE_PHOTO_HINT}</p>
                        <p className="profile-edit-sheet-error" role="alert">{photoError}</p>
                    </div>
                </div>
                <LockedField label="EMAIL" value={email} reason="Email cannot be changed here" />
                <LockedField label="USERNAME" value={profile.username} reason="Username cannot be changed" />
                <div className="profile-edit-sheet-field">
                    <label className="profile-edit-sheet-label" htmlFor={bioId}>BIO</label>
                    <textarea
                        id={bioId}
                        className="profile-edit-sheet-text"
                        aria-invalid={edit.isBioOver || edit.errors.bio !== ''}
                        aria-describedby={`${counterId} ${bioErrorId}`}
                        value={edit.bio}
                        rows={2}
                        ref={bioRef}
                        onChange={(event) => handleBioChange(event.currentTarget.value)}
                    />
                    <div className="profile-edit-sheet-rule" aria-hidden="true" />
                    <p id={counterId} className="profile-edit-sheet-counter" data-over-limit={edit.isBioOver}>
                        {countProfileCharacters(edit.bio)} / {PROFILE_BIO_MAX_CODE_POINTS}
                    </p>
                    <p id={bioErrorId} className="profile-edit-sheet-error">{edit.errors.bio}</p>
                    <p className="sr-only" aria-live="polite">{overLimitAnnouncement}</p>
                </div>
                <div className="profile-edit-sheet-field">
                    <label className="profile-edit-sheet-label" htmlFor={locationId}>LOCATION</label>
                    <input
                        id={locationId}
                        type="text"
                        className="profile-edit-sheet-input"
                        aria-invalid={locationError !== ''}
                        aria-describedby={locationErrorId}
                        value={edit.location}
                        placeholder="City, Country"
                        autoComplete="off"
                        onChange={(event) => handleLocationChange(event.currentTarget.value)}
                    />
                    <div className="profile-edit-sheet-rule" aria-hidden="true" />
                    <p id={locationErrorId} className="profile-edit-sheet-error">{locationError}</p>
                </div>
                <div className="profile-edit-sheet-actions">
                    <button
                        type="submit"
                        className="profile-edit-sheet-save"
                        aria-disabled={isSaveBlocked}
                        aria-busy={edit.isSaving}
                    >
                        {edit.isSaving ? 'SAVING' : 'SAVE CHANGES'}
                    </button>
                    <button
                        type="button"
                        className="profile-edit-sheet-cancel"
                        data-armed={isArmed}
                        onClick={handleCancel}
                        onBlur={discard.reset}
                    >
                        {isArmed ? 'DISCARD CHANGES?' : 'CANCEL ESC'}
                    </button>
                </div>
                <p className="profile-edit-sheet-error" role="alert">{edit.errors.general}</p>
                <p className="sr-only" aria-live="polite">{isArmed ? 'Press again to discard your changes' : ''}</p>
            </form>
        </div>
    );
}

export default ProfileEditSheet;
