import { useEffect, useRef, useState } from 'react';
import { createAsset, fetchAssetImageDataUrl, updateAsset } from '../../services/assetService';
import { useModalBehavior } from '../../hooks/useModalBehavior';
import { ApiError } from '../../types/problem';
import type { Asset, AssetRequest } from '../../types/asset';
import styles from './AssetForm.module.css';


interface AssetFormProps {
    // The asset being edited, or null when creating a new one.
    asset: Asset | null;
    onSaved: () => void;
    onCancel: () => void;
}


type FieldErrors = Partial<Record<keyof AssetRequest, string>>;


// Keeps only the keys this form actually renders, so an unexpected server field doesn't get
// silently dropped into state where nothing displays it.
const pickFieldErrors = (errors: Record<string, string>): FieldErrors => {
    const mapped: FieldErrors = {};

    if (errors.name !== undefined) {
        mapped.name = errors.name;
    }
    if (errors.description !== undefined) {
        mapped.description = errors.description;
    }
    if (errors.imageBase64 !== undefined) {
        mapped.imageBase64 = errors.imageBase64;
    }

    return mapped;
};


/**
 * Create/edit form for a catalog asset. On edit it fetches the current image back (the list
 * DTO omits the blob) so the moderator can change name or description without re-uploading.
 */
export const AssetForm = ({ asset, onSaved, onCancel }: AssetFormProps) => {
    const isEdit = asset !== null;

    const formRef = useModalBehavior<HTMLFormElement>(onCancel);

    const [name, setName] = useState(asset?.name ?? '');
    const [description, setDescription] = useState(asset?.description ?? '');
    const [imageBase64, setImageBase64] = useState('');
    const [fileName, setFileName] = useState(asset?.imageName ?? '');
    const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
    const [formError, setFormError] = useState<string | null>(null);
    const [submitting, setSubmitting] = useState(false);

    const fileInputRef = useRef<HTMLInputElement>(null);

    // Prefill the existing image so an edit that only touches text keeps the current picture.
    useEffect(() => {
        if (asset === null) {
            return;
        }

        let active = true;

        fetchAssetImageDataUrl(asset.id)
            .then((dataUrl) => {
                if (active) {
                    setImageBase64(dataUrl);
                }
            })
            .catch(() => {
                // Non-fatal: the moderator can still pick a fresh image before saving.
            });

        return () => {
            active = false;
        };
    }, [asset]);

    const handleImagePick = (event: React.ChangeEvent<HTMLInputElement>): void => {
        const file = event.target.files?.[0];

        if (file === undefined) {
            return;
        }

        setFileName(file.name);

        const reader = new FileReader();
        reader.onload = () => {
            if (typeof reader.result === 'string') {
                setImageBase64(reader.result);
            }
        };
        reader.readAsDataURL(file);
    };

    const openFilePicker = (): void => {
        fileInputRef.current?.click();
    };

    const pickerStatus = (): string => {
        return fileName.length > 0 ? fileName : 'no file selected';
    };

    const handleSubmit = async (event: React.FormEvent<HTMLFormElement>): Promise<void> => {
        event.preventDefault();

        const clientErrors = validate();
        if (Object.keys(clientErrors).length > 0) {
            setFieldErrors(clientErrors);

            return;
        }

        setSubmitting(true);
        setFormError(null);
        setFieldErrors({});

        const request: AssetRequest = {
            name: name.trim(),
            description: description.trim(),
            imageBase64,
            imageName: fileName,
        };

        try {
            await persist(request);
            onSaved();
        } catch (error) {
            applyError(error);
        } finally {
            setSubmitting(false);
        }
    };

    // Mirrors HoldingForm: surface the missing requirement on submit rather than silently
    // disabling save, so the moderator knows what's left to fill in.
    const validate = (): FieldErrors => {
        const errors: FieldErrors = {};

        if (name.trim().length === 0) {
            errors.name = 'Name is required.';
        }
        if (description.trim().length === 0) {
            errors.description = 'Description is required.';
        }
        if (imageBase64.length === 0) {
            errors.imageBase64 = 'An image is required.';
        }

        return errors;
    };

    const persist = (request: AssetRequest): Promise<Asset> => {
        if (isEdit) {
            return updateAsset(asset.id, request);
        }

        return createAsset(request);
    };

    const applyError = (error: unknown): void => {
        if (error instanceof ApiError) {
            const mapped = pickFieldErrors(error.fieldErrors);

            setFieldErrors(mapped);
            setFormError(Object.keys(mapped).length > 0 ? null : error.detail);

            return;
        }

        setFormError('Could not reach the server. Try again.');
    };

    return (
        <div className={styles.backdrop} onClick={onCancel} role="presentation">
            <form
                ref={formRef}
                className={styles.form}
                onClick={(event) => event.stopPropagation()}
                onSubmit={handleSubmit}
                role="dialog"
                aria-modal="true"
                aria-label={isEdit ? 'Edit asset' : 'New asset'}
                tabIndex={-1}
                noValidate
            >
                <h2 className={styles.heading}>{isEdit ? 'EDIT ASSET' : 'NEW ASSET'}</h2>

                <label className={styles.field}>
                    <span className={styles.label}>NAME</span>
                    <input
                        className={styles.input}
                        type="text"
                        value={name}
                        maxLength={100}
                        autoFocus
                        aria-invalid={fieldErrors.name !== undefined}
                        aria-describedby={fieldErrors.name !== undefined ? 'asset-name-error' : undefined}
                        onChange={(event) => setName(event.target.value)}
                    />
                    {fieldErrors.name !== undefined && (
                        <span id="asset-name-error" className={styles.fieldError}>{fieldErrors.name}</span>
                    )}
                </label>

                <label className={styles.field}>
                    <span className={styles.label}>DESCRIPTION</span>
                    <textarea
                        className={styles.textarea}
                        value={description}
                        maxLength={1000}
                        rows={4}
                        aria-invalid={fieldErrors.description !== undefined}
                        aria-describedby={fieldErrors.description !== undefined ? 'asset-description-error' : undefined}
                        onChange={(event) => setDescription(event.target.value)}
                    />
                    {fieldErrors.description !== undefined && (
                        <span id="asset-description-error" className={styles.fieldError}>{fieldErrors.description}</span>
                    )}
                </label>

                <div className={styles.field}>
                    <span className={styles.label}>IMAGE</span>

                    <input
                        ref={fileInputRef}
                        className={styles.hiddenFileInput}
                        type="file"
                        accept="image/*"
                        aria-label="Image"
                        aria-invalid={fieldErrors.imageBase64 !== undefined}
                        aria-describedby={fieldErrors.imageBase64 !== undefined ? 'asset-image-error' : undefined}
                        onChange={handleImagePick}
                    />

                    <div className={styles.filePicker}>
                        <button type="button" className={styles.fileButton} onClick={openFilePicker}>
                            {imageBase64.length > 0 ? 'change image' : 'choose image'}
                        </button>

                        <span className={styles.fileName}>{pickerStatus()}</span>
                    </div>

                    {fieldErrors.imageBase64 !== undefined && (
                        <span id="asset-image-error" className={styles.fieldError}>{fieldErrors.imageBase64}</span>
                    )}
                </div>

                {formError !== null && (
                    <p className={styles.formError} role="alert">! {formError}</p>
                )}

                <div className={styles.actions}>
                    <button type="submit" className={styles.save} disabled={submitting}>
                        {submitting ? '[ ... ]' : 'save'}
                    </button>

                    <button type="button" className={styles.cancel} onClick={onCancel}>
                        cancel
                    </button>
                </div>
            </form>
        </div>
    );
};
