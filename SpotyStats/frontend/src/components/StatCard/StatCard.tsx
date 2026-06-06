import { Skeleton } from '../Skeleton/Skeleton'
import styles from './StatCard.module.css'


interface StatCardProps {
  label: string
  value: string
  sublabel: string
}

export const StatCard = ({ label, value, sublabel }: StatCardProps) => (
  <div className={styles.card}>
    <div className={styles.label}>{label}</div>
    <div className={styles.value}>{value}</div>
    <div className={styles.sublabel}>{sublabel}</div>
  </div>
)

export const StatCardSkeleton = () => (
  <div className={styles.card}>
    <Skeleton width="60%" height="11px" />
    <div className={styles.skeletonGap}>
      <Skeleton width="50%" height="38px" />
    </div>
    <div className={styles.skeletonGap}>
      <Skeleton width="70%" height="12px" />
    </div>
  </div>
)
