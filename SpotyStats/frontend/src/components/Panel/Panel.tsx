import type { ReactNode } from 'react'
import styles from './Panel.module.css'


interface PanelProps {
  children: ReactNode
}

/** Standard glass surface: panel bg, border, blur, radius 20, padding 32/32/28. */
export const Panel = ({ children }: PanelProps) => (
  <section className={styles.panel}>{children}</section>
)
