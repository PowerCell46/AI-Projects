import type { CSSProperties } from 'react'
import styles from './Skeleton.module.css'


interface SkeletonProps {
  width?: string
  height?: string
  radius?: string
}

/** Shimmer placeholder block — panels show these while fetching. No spinners. */
export const Skeleton = ({ width = '100%', height = '16px', radius = '8px' }: SkeletonProps) => {
  const style: CSSProperties = { width, height, borderRadius: radius }

  return <div className={styles.block} style={style} />
}
