import { formatMoney, formatPrice, formatQuantity } from '../../utils/format';
import { isoToDisplay } from '../../utils/date';
import { useModalBehavior } from '../../hooks/useModalBehavior';
import type { Holding } from '../../types/holding';
import styles from './HoldingDetail.module.css';


interface HoldingDetailProps {
    holding: Holding;
    onEdit: () => void;
    onClose: () => void;
}


/**
 * Read-only view of a single holding, opened by clicking its row. Mirrors the edit modal's
 * framing so the two read as the same surface, but every figure is presented rather than editable;
 * the full note is shown here instead of being crammed into the table.
 */
export const HoldingDetail = ({ holding, onEdit, onClose }: HoldingDetailProps) => {
    const panelRef = useModalBehavior<HTMLDivElement>(onClose);

    return (
        <div className={styles.backdrop} onClick={onClose} role="presentation">
            <div
                ref={panelRef}
                className={styles.panel}
                onClick={(event) => event.stopPropagation()}
                role="dialog"
                aria-modal="true"
                aria-label={`Holding: ${holding.name}`}
                tabIndex={-1}
            >
                <h2 className={styles.heading}>HOLDING</h2>

                <div className={styles.field}>
                    <span className={styles.label}>NAME</span>
                    <span className={styles.value}>{holding.name}</span>
                </div>

                <div className={styles.pair}>
                    <div className={styles.field}>
                        <span className={styles.label}>TOTAL COST</span>
                        <span className={styles.value}>{formatMoney(holding.boughtForAmount)}</span>
                    </div>

                    <div className={styles.field}>
                        <span className={styles.label}>QUANTITY</span>
                        <span className={styles.value}>
                            {formatQuantity(holding.quantity)} {holding.unit}
                        </span>
                    </div>
                </div>

                <div className={styles.pair}>
                    <div className={styles.field}>
                        <span className={styles.label}>BOUGHT AT PRICE</span>
                        <span className={styles.value}>{formatPrice(holding.price)}</span>
                    </div>

                    <div className={styles.field}>
                        <span className={styles.label}>PURCHASE DATE</span>
                        <span className={styles.value}>{isoToDisplay(holding.date)}</span>
                    </div>
                </div>

                <div className={styles.field}>
                    <span className={styles.label}>NOTE</span>
                    <p className={styles.note}>
                        {holding.note !== null && holding.note.length > 0
                            ? holding.note
                            : <span className={styles.empty}>—</span>}
                    </p>
                </div>

                <div className={styles.actions}>
                    <button type="button" className={styles.edit} onClick={onEdit}>
                        edit
                    </button>

                    <button type="button" className={styles.close} onClick={onClose}>
                        close
                    </button>
                </div>
            </div>
        </div>
    );
};
