import { useState } from 'react';
import styles from './InlineDeleteConfirm.module.css';


interface InlineDeleteConfirmProps {
    onEdit: () => void;
    onDelete: () => void;
    // Disables the delete control (e.g. an asset still referenced by holdings) and explains why.
    deleteDisabled?: boolean;
    deleteDisabledReason?: string;
}


/**
 * Row action cluster with a two-step inline delete: the first click on "delete" swaps the buttons
 * for a "delete? yes/no" prompt so a stray click can't drop a record. Owns its own confirm state,
 * resetting on either choice; the parent only handles the actual edit/delete.
 */
export const InlineDeleteConfirm = ({
    onEdit,
    onDelete,
    deleteDisabled = false,
    deleteDisabledReason,
}: InlineDeleteConfirmProps) => {
    const [confirming, setConfirming] = useState(false);

    const confirm = (): void => {
        setConfirming(false);
        onDelete();
    };

    if (confirming) {
        return (
            <div className={styles.actions}>
                <span className={styles.confirmPrompt}>delete?</span>
                <button type="button" className={styles.confirmYes} onClick={confirm}>
                    yes
                </button>
                <button type="button" className={styles.action} onClick={() => setConfirming(false)}>
                    no
                </button>
            </div>
        );
    }

    return (
        <div className={styles.actions}>
            <button type="button" className={styles.action} onClick={onEdit}>
                edit
            </button>
            <button
                type="button"
                className={styles.action}
                disabled={deleteDisabled}
                title={deleteDisabled ? deleteDisabledReason : undefined}
                onClick={() => setConfirming(true)}
            >
                delete
            </button>
        </div>
    );
};
